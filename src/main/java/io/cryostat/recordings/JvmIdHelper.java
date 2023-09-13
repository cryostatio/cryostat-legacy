/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.recordings;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
import io.cryostat.core.net.JFRConnection;
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
import org.apache.commons.lang3.StringUtils;

public class JvmIdHelper extends AbstractEventEmitter<JvmIdHelper.IdEvent, String> {

    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final long connectionTimeoutSeconds;
    private final Base32 base32;
    private final Logger logger;

    private final AsyncLoadingCache<String, String> ids;
    private final Map<String, ServiceRef> reverse;

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
        this.reverse = new HashMap<>();
    }

    private boolean observe(ServiceRef sr) {
        if (StringUtils.isBlank(sr.getJvmId())) {
            return false;
        }
        reverse.put(sr.getJvmId(), sr);
        ids.put(sr.getServiceUri().toString(), CompletableFuture.completedFuture(sr.getJvmId()));
        return true;
    }

    // Use dao directly since refs resolve before listDiscoverableServices is populated
    public ServiceRef resolveId(ServiceRef sr) throws JvmIdGetException {
        if (observe(sr)) {
            return sr;
        }
        logger.info("Observing new target: {}", sr);
        URI serviceUri = sr.getServiceUri();
        String uriStr = serviceUri.toString();
        try {
            String id =
                    computeJvmId(uriStr, Optional.ofNullable(credentialsManager.getCredentials(sr)))
                            .whenComplete(
                                    (i, t) -> {
                                        String prevId = this.ids.synchronous().get(uriStr);
                                        if (Objects.equals(prevId, i)) {
                                            return;
                                        }
                                        this.ids.put(uriStr, CompletableFuture.completedFuture(i));
                                        logger.info("JVM ID: {} -> {}", uriStr, i);
                                    })
                            .get();

            ServiceRef updated = new ServiceRef(id, serviceUri, sr.getAlias().orElse(uriStr));
            updated.setLabels(sr.getLabels());
            updated.setPlatformAnnotations(sr.getPlatformAnnotations());
            updated.setCryostatAnnotations(sr.getCryostatAnnotations());
            reverse.put(id, sr);
            return updated;
        } catch (InterruptedException | ExecutionException | ScriptException e) {
            logger.warn("Could not resolve jvmId for target {}", uriStr);
            throw new JvmIdGetException(e, uriStr);
        }
    }

    public Optional<ServiceRef> reverseLookup(String jvmId) {
        return Optional.ofNullable(this.reverse.get(jvmId));
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
                        JFRConnection::getJvmId);
        future.thenAccept(id -> logger.info("JVM ID: {} -> {}", targetId, id));
        return future;
    }

    public String getJvmId(ConnectionDescriptor connectionDescriptor) throws JvmIdGetException {
        return getJvmId(connectionDescriptor.getTargetId(), true, Optional.empty());
    }

    public String getJvmId(String targetId) throws JvmIdGetException {
        return getJvmId(targetId, true, Optional.empty());
    }

    public String getJvmId(String targetId, boolean cache, Optional<Credentials> credentials)
            throws JvmIdGetException {
        try {
            return (cache ? this.ids.get(targetId) : computeJvmId(targetId, credentials))
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

    public boolean isSpecialDirectory(String directoryName) {
        return directoryName.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)
                || directoryName.equals(RecordingArchiveHelper.TEMP_UPLOADS_SUBDIRECTORY)
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

    public static class JvmIdDoesNotExistException extends IOException {
        public JvmIdDoesNotExistException(String jvmId) {
            super(jvmId);
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
