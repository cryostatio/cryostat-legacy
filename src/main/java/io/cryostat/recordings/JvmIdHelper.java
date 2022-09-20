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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

public class JvmIdHelper {
    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final Logger logger;

    private final Map<String, String> jvmIdMap;

    public static final int CONNECT_TIMEOUT_SECONDS = 1;

    JvmIdHelper(
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            Logger logger) {

        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.logger = logger;
        this.jvmIdMap = new ConcurrentHashMap<>();
    }

    private String computeJvmId(ConnectionDescriptor cd) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (cd.getTargetId().equals(RecordingArchiveHelper.ARCHIVES)
                || cd.getTargetId()
                        .equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;
        }
        try {
            if (cd.getCredentials().isEmpty()) {
                cd =
                        new ConnectionDescriptor(
                                cd.getTargetId(),
                                credentialsManager.getCredentialsByTargetId(cd.getTargetId()));
            }
            final ConnectionDescriptor desc = cd;
            CompletableFuture.runAsync(
                    () -> {
                        try {
                            this.targetConnectionManager.executeConnectedTask(
                                    desc,
                                    connection -> {
                                        return future.complete(connection.getJvmId());
                                    });
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    });
            return future.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error(e);
            return null;
        }
    }

    public String getJvmId(ConnectionDescriptor connectionDescriptor) throws JvmIdGetException {
        String targetId = connectionDescriptor.getTargetId();
        // FIXME: this should be fixed after the 2.2.0 release
        if (targetId.equals(RecordingArchiveHelper.ARCHIVES)
                || targetId.equals(RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;
        }
        String jvmId =
                this.jvmIdMap.computeIfAbsent(
                        targetId,
                        k -> {
                            return computeJvmId(connectionDescriptor);
                        });
        if (jvmId == null) {
            throw new JvmIdGetException(String.format("Error connecting to target %s", targetId));
        }
        return jvmId;
    }

    public String getJvmId(String targetId) throws JvmIdGetException {
        return getJvmId(new ConnectionDescriptor(targetId));
    }

    public void transferJvmIds(String oldJvmId, String newJvmId) throws IOException {
        if (oldJvmId.equals(newJvmId)) {
            return;
        }
        jvmIdMap.entrySet().stream()
                .filter(e -> e.getValue().equals(oldJvmId))
                .forEach(
                        e -> {
                            jvmIdMap.put(e.getKey(), newJvmId);
                        });
    }

    protected String get(String targetId) {
        return jvmIdMap.get(targetId);
    }

    protected void put(String targetId, String jvmId) {
        jvmIdMap.put(targetId, jvmId);
    }

    protected String putIfAbsent(String targetId, String jvmId) {
        return jvmIdMap.putIfAbsent(targetId, jvmId);
    }

    static class JvmIdGetException extends IOException {
        JvmIdGetException(String message) {
            super(message);
        }
    }
}
