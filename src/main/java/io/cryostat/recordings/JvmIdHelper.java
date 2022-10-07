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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

public class JvmIdHelper {

    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final long connectionTimeoutSeconds;
    private final Logger logger;

    private final AsyncLoadingCache<String, String> ids;

    JvmIdHelper(
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            long connectionTimeoutSeconds,
            Executor executor,
            Scheduler scheduler,
            Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.logger = logger;
        this.ids =
                Caffeine.newBuilder()
                        .executor(executor)
                        .scheduler(scheduler)
                        .buildAsync(new IdLoader());
    }

    protected CompletableFuture<String> computeJvmId(String targetId)
            throws ScriptException {
        // FIXME: this should be refactored after the 2.2.0 release
        if (targetId == null
                || targetId.equals(RecordingArchiveHelper.ARCHIVES)
                || targetId.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return CompletableFuture.completedFuture(
                    RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY);
        }
        return this.targetConnectionManager.executeConnectedTaskAsync(
                new ConnectionDescriptor(
                        targetId, credentialsManager.getCredentialsByTargetId(targetId)),
                connection -> {
                    try {
                        return connection.getJvmId();
                    } catch (Exception e) {
                        throw new JvmIdGetException(e, targetId);
                    }
                });
    }

    public String getJvmId(ConnectionDescriptor connectionDescriptor) throws JvmIdGetException {
        return getJvmId(connectionDescriptor.getTargetId());
    }

    public String getJvmId(String targetId) throws JvmIdGetException {
        try {
            return this.ids
                    .get(targetId)
                    .get(connectionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new JvmIdGetException(e, targetId);
        }
    }

    public void transferJvmIds(String oldJvmId, String newJvmId) throws IOException {
        if (oldJvmId.equals(newJvmId)) {
            return;
        }
        ids.asMap().entrySet().stream()
                .filter(
                        entry -> {
                            try {
                                return entry.getValue().get().equals(oldJvmId);
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error(e);
                                return false;
                            }
                        })
                .forEach(e -> ids.put(e.getKey(), CompletableFuture.completedFuture(newJvmId)));
    }

    protected Future<String> get(String targetId) {
        return ids.get(targetId);
    }

    protected void remove(String targetId) {
        ids.synchronous().invalidate(targetId);
    }

    protected void put(String targetId, String jvmId) {
        ids.put(targetId, CompletableFuture.completedFuture(jvmId));
    }

    protected Future<String> putIfAbsent(String targetId, String jvmId) {
        return ids.asMap().putIfAbsent(targetId, CompletableFuture.completedFuture(jvmId));
    }

    static class JvmIdGetException extends IOException {
        private String targetId;

        JvmIdGetException(String message, String targetId) {
            super(message);
            this.targetId = targetId;
        }

        JvmIdGetException(Throwable cause, String targetId) {
            super(cause);
            this.targetId = targetId;
        }

        public String getTarget() {
            return targetId;
        }
    }

    private class IdLoader implements AsyncCacheLoader<String, String> {

        @Override
        public CompletableFuture<String> asyncLoad(String key, Executor executor)
                throws Exception {
            return computeJvmId(key);
        }

        @Override
        public CompletableFuture<String> asyncReload(
                String key, String prev, Executor executor) throws Exception {
            return asyncLoad(key, executor);
        }
    }
}
