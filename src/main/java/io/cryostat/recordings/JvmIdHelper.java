/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.recordings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.util.events.AbstractEventEmitter;
import io.cryostat.util.events.EventType;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.apache.commons.codec.binary.Base32;

public class JvmIdHelper extends AbstractEventEmitter<JvmIdHelper.IdEvent, String> {

    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final long connectionTimeoutSeconds;
    private final Base32 base32;
    private final Logger logger;

    private final AsyncLoadingCache<String, String> ids;

    JvmIdHelper(
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            PlatformClient platform,
            long connectionTimeoutSeconds,
            Executor executor,
            Scheduler scheduler,
            Base32 base32,
            Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.base32 = base32;
        this.logger = logger;
        this.ids =
                Caffeine.newBuilder()
                        .executor(executor)
                        .scheduler(scheduler)
                        .<String, String>removalListener(
                                (targetId, jvmId, cause) -> emit(IdEvent.INVALIDATED, jvmId))
                        .buildAsync(new IdLoader());

        platform.addTargetDiscoveryListener(
                tde -> {
                    switch (tde.getEventKind()) {
                        case LOST:
                            String targetId = tde.getServiceRef().getServiceUri().toString();
                            ids.synchronous().invalidate(targetId);
                            break;
                        default:
                            // ignored
                            break;
                    }
                });
    }

    // Use dao directly since refs resolve before listDiscoverableServices is populated
    public ServiceRef resolveId(ServiceRef sr) throws JvmIdGetException {
        if (sr.getJvmId() != null) return sr;
        try {
            CompletableFuture<String> future =
                    this.targetConnectionManager.executeConnectedTaskAsync(
                            new ConnectionDescriptor(
                                    sr.getServiceUri().toString(),
                                    credentialsManager.getCredentials(sr)),
                            connection -> {
                                return connection.getJvmId();
                            });
            future.thenAccept(
                    id -> {
                        this.ids.synchronous().put(sr.getServiceUri().toString(), id);
                        logger.info("JVM ID: {} -> {}", sr.getServiceUri().toString(), id);
                    });
            String id = future.get(connectionTimeoutSeconds, TimeUnit.SECONDS);

            ServiceRef updated =
                    new ServiceRef(
                            id,
                            sr.getServiceUri(),
                            sr.getAlias().orElse(sr.getServiceUri().toString()));
            updated.setLabels(sr.getLabels());
            updated.setPlatformAnnotations(sr.getPlatformAnnotations());
            updated.setCryostatAnnotations(sr.getCryostatAnnotations());
            return updated;
        } catch (InterruptedException | ExecutionException | TimeoutException | ScriptException e) {
            logger.warn("Could not get jvmId for target {}", sr.getServiceUri().toString());
            throw new JvmIdGetException(e, sr.getServiceUri().toString());
        }
    }

    private CompletableFuture<String> computeJvmId(
            String targetId, Optional<Credentials> credentials) throws ScriptException {
        // FIXME: this should be refactored after the 2.2.0 release
        if (targetId == null
                || targetId.equals(RecordingArchiveHelper.ARCHIVES)
                || targetId.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return CompletableFuture.completedFuture(
                    RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY);
        } else if (targetId.equals(RecordingArchiveHelper.LOST_RECORDINGS_SUBDIRECTORY)) {
            return CompletableFuture.completedFuture(
                    RecordingArchiveHelper.LOST_RECORDINGS_SUBDIRECTORY);
        }
        CompletableFuture<String> future =
                this.targetConnectionManager.executeConnectedTaskAsync(
                        new ConnectionDescriptor(
                                targetId,
                                credentials.isPresent()
                                        ? credentials.get()
                                        : credentialsManager.getCredentialsByTargetId(targetId)),
                        connection -> {
                            return connection.getJvmId();
                        });
        future.thenAccept(id -> logger.info("JVM ID: {} -> {}", targetId, id));
        return future;
    }

    public String getJvmId(ConnectionDescriptor connectionDescriptor) throws JvmIdGetException {
        return getJvmId(connectionDescriptor.getTargetId(), true, Optional.empty());
    }

    public String getJvmId(String targetId) throws JvmIdGetException {
        return getJvmId(targetId, true, Optional.empty());
    }

    public String getJvmId(String targetId, boolean saveToCached, Optional<Credentials> credentials)
            throws JvmIdGetException {
        try {
            return (saveToCached ? this.ids.get(targetId) : computeJvmId(targetId, credentials))
                    .get(connectionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException | ScriptException e) {
            logger.warn("Could not get jvmId for target {}", targetId);
            throw new JvmIdGetException(e, targetId);
        }
    }

    public String subdirectoryNameToJvmId(String subdirectoryName) {
        if (subdirectoryName.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)
                || subdirectoryName.equals(RecordingArchiveHelper.LOST_RECORDINGS_SUBDIRECTORY)) {
            return subdirectoryName;
        }
        return new String(base32.decode(subdirectoryName), StandardCharsets.UTF_8);
    }

    public String jvmIdToSubdirectoryName(String jvmId) {
        if (jvmId.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)
                || jvmId.equals(RecordingArchiveHelper.LOST_RECORDINGS_SUBDIRECTORY)) {
            return jvmId;
        }
        return base32.encodeAsString(jvmId.getBytes(StandardCharsets.UTF_8));
    }

    // FIXME: refactor structure to remove file-uploads (v1 RecordingsPostBodyHandler)
    public boolean isSpecialDirectory(String directoryName) {
        return directoryName.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)
                || directoryName.equals("file-uploads")
                || directoryName.equals(RecordingArchiveHelper.LOST_RECORDINGS_SUBDIRECTORY);
    }

    public static class JvmIdGetException extends IOException {
        private String targetId;

        JvmIdGetException(String message, String targetId) {
            super(message);
            this.targetId = targetId;
        }

        public JvmIdGetException(Throwable cause, String targetId) {
            super(cause);
            this.targetId = targetId;
        }

        public String getTarget() {
            return targetId;
        }
    }

    private class IdLoader implements AsyncCacheLoader<String, String> {

        @Override
        public CompletableFuture<String> asyncLoad(String key, Executor executor) throws Exception {
            return computeJvmId(key, Optional.empty());
        }

        @Override
        public CompletableFuture<String> asyncReload(String key, String prev, Executor executor)
                throws Exception {
            return asyncLoad(key, executor);
        }
    }

    public enum IdEvent implements EventType {
        INVALIDATED,
    }
}
