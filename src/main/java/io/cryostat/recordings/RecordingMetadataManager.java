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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.TargetDiscoveryEvent;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingMetadataManager extends AbstractVerticle
        implements Consumer<TargetDiscoveryEvent> {

    public static final String NOTIFICATION_CATEGORY = "RecordingMetadataUpdated";
    private static final int STALE_METADATA_TIMEOUT_SECONDS = 5;

    private final Path recordingMetadataDir;
    private final FileSystem fs;
    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final NotificationFactory notificationFactory;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final Map<Pair<String, String>, Metadata> recordingMetadataMap;
    private final Map<String, String> jvmIdMap;
    private final Map<String, Long> staleMetadataTimers;

    RecordingMetadataManager(
            Vertx vertx,
            Path recordingMetadataDir,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            Gson gson,
            Base32 base32,
            Logger logger) {
        this.vertx = vertx;
        this.recordingMetadataDir = recordingMetadataDir;
        this.fs = fs;
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.notificationFactory = notificationFactory;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
        this.recordingMetadataMap = new ConcurrentHashMap<>();
        this.jvmIdMap = new ConcurrentHashMap<>();
        this.staleMetadataTimers = new ConcurrentHashMap<>();
    }

    public void load() throws IOException {
        this.fs.listDirectoryChildren(recordingMetadataDir).stream()
                .peek(n -> logger.trace("Recording Metadata file: {}", n))
                .map(recordingMetadataDir::resolve)
                .map(
                        path -> {
                            try {
                                return fs.readFile(path);
                            } catch (IOException e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .map(reader -> gson.fromJson(reader, StoredRecordingMetadata.class))
                .forEach(
                        srm -> {
                            recordingMetadataMap.put(
                                    Pair.of(srm.getJvmId(), srm.getRecordingName()), srm);
                            jvmIdMap.putIfAbsent(srm.getTargetId(), srm.getJvmId());
                        });
    }

    // when targets disappear or are restarted, update the jvmIds mapped
    // to each target's recording metadata
    // if targets are restarted, delete any stale metadata from the previous container
    @Override
    public synchronized void accept(TargetDiscoveryEvent tde) {
        String targetId = tde.getServiceRef().getServiceUri().toString();

        Credentials credentials;
        ConnectionDescriptor cd;

        try {
            credentials = credentialsManager.getCredentials(tde.getServiceRef());
            cd = new ConnectionDescriptor(tde.getServiceRef(), credentials);
        } catch (IOException | ScriptException e) {
            logger.error(e);
            return;
        }

        String oldJvmId = jvmIdMap.get(targetId);

        if (oldJvmId == null) {
            return;
        }

        switch (tde.getEventKind()) {
            case FOUND:
                try {
                    String newJvmId =
                            this.targetConnectionManager.executeConnectedTask(
                                    cd,
                                    connection -> {
                                        try {
                                            return connection.getJvmId();
                                        } catch (Exception e) {
                                            logger.error(e);
                                            return null;
                                        }
                                    });

                    if (oldJvmId.equals(newJvmId)) {
                        Long id = staleMetadataTimers.remove(oldJvmId);
                        if (id != null) {
                            this.vertx.cancelTimer(id);
                        }
                        return;
                    }

                    recordingMetadataMap.keySet().stream()
                            .filter(keyPair -> keyPair.getKey().equals(oldJvmId))
                            .forEach(
                                    keyPair -> {
                                        try {
                                            String recordingName = keyPair.getValue();
                                            Metadata m =
                                                    deleteRecordingMetadataIfExists(
                                                            cd, recordingName);
                                            // FIXME preserve archived recording metadata
                                            //jvmIdMap.put(targetId, newJvmId);
                                            // setRecordingMetadata(cd, recordingName, m);
                                            //jvmIdMap.put(targetId, oldJvmId);
                                        } catch (IOException e) {
                                            logger.error(e);
                                        }
                                    });
                    jvmIdMap.put(targetId, newJvmId);
                } catch (Exception e) {
                    logger.error(e);
                }
                break;
            case LOST:
                staleMetadataTimers.computeIfAbsent(
                        oldJvmId,
                        k ->
                                this.vertx.setTimer(
                                        Duration.ofSeconds(STALE_METADATA_TIMEOUT_SECONDS)
                                                .toMillis(),
                                        initialId -> {
                                            recordingMetadataMap.keySet().stream()
                                                    .filter(
                                                            keyPair ->
                                                                    keyPair.getKey()
                                                                            .equals(oldJvmId))
                                                    .forEach(
                                                            keyPair -> {
                                                                try {
                                                                    String recordingName =
                                                                            keyPair.getValue();

                                                                    Path targetSubDirectory =
                                                                            Path.of(
                                                                                    base32
                                                                                            .encodeAsString(
                                                                                                    targetId
                                                                                                            .getBytes(
                                                                                                                    StandardCharsets
                                                                                                                            .UTF_8)));

                                                                    boolean isActiveRecording =
                                                                            this.fs
                                                                                    .listDirectoryChildren(
                                                                                            targetSubDirectory)
                                                                                    .stream()
                                                                                    .anyMatch(
                                                                                            filename ->
                                                                                                    filename
                                                                                                            .equals(
                                                                                                                    recordingName));

                                                                    if (isActiveRecording) {
                                                                        deleteRecordingMetadataIfExists(
                                                                                cd, recordingName);
                                                                    }
                                                                } catch (IOException e) {
                                                                    logger.error(e);
                                                                }
                                                            });
                                        }));

                break;
            default:
                throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor,
            String recordingName,
            Metadata metadata,
            boolean issueNotification)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);

        String jvmId = this.getJvmId(connectionDescriptor);

        this.recordingMetadataMap.put(Pair.of(jvmId, recordingName), metadata);
        fs.writeString(
                this.getMetadataPath(jvmId, recordingName),
                gson.toJson(
                        StoredRecordingMetadata.of(
                                connectionDescriptor.getTargetId(),
                                jvmId,
                                recordingName,
                                metadata)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        if (issueNotification) {
            notificationFactory
                    .createBuilder()
                    .metaCategory(RecordingMetadataManager.NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(
                            Map.of(
                                    "recordingName",
                                    recordingName,
                                    "target",
                                    connectionDescriptor.getTargetId(),
                                    "metadata",
                                    metadata))
                    .build()
                    .send();
        }

        return CompletableFuture.completedFuture(metadata);
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor, String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return setRecordingMetadata(connectionDescriptor, recordingName, metadata, false);
    }

    public Future<Metadata> setRecordingMetadata(String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return this.setRecordingMetadata(
                new ConnectionDescriptor(RecordingArchiveHelper.ARCHIVES),
                recordingName,
                metadata);
    }

    public Metadata getMetadata(ConnectionDescriptor connectionDescriptor, String recordingName)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);

        String jvmId = this.getJvmId(connectionDescriptor);
        return this.recordingMetadataMap.computeIfAbsent(
                Pair.of(jvmId, recordingName), k -> new Metadata());
    }

    public Metadata deleteRecordingMetadataIfExists(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        String jvmId = this.getJvmId(connectionDescriptor);

        Metadata deleted = this.recordingMetadataMap.remove(Pair.of(jvmId, recordingName));
        fs.deleteIfExists(this.getMetadataPath(jvmId, recordingName));
        return deleted;
    }

    public Future<Metadata> copyMetadataToArchives(
            ConnectionDescriptor connectionDescriptor, String recordingName, String filename)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(filename);
        Metadata metadata = this.getMetadata(connectionDescriptor, recordingName);
        return this.setRecordingMetadata(connectionDescriptor, filename, metadata);
    }

    public Map<String, String> parseRecordingLabels(String labels) throws IllegalArgumentException {
        Objects.requireNonNull(labels, "Labels must not be null");

        try {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> parsedLabels = gson.fromJson(labels, mapType);
            if (parsedLabels == null) {
                throw new IllegalArgumentException(labels);
            }
            Pattern noWhitespace = Pattern.compile("^(\\S)+$");

            for (var label : parsedLabels.entrySet()) {
                Matcher keyMatcher = noWhitespace.matcher(label.getKey());
                Matcher valueMatcher = noWhitespace.matcher(label.getValue());

                if (!keyMatcher.matches() || !valueMatcher.matches()) {
                    throw new IllegalArgumentException("Labels must not contain whitespace");
                }
            }
            return parsedLabels;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Path getMetadataPath(String jvmId, String recordingName) {
        String filename = String.format("%s%s", jvmId, recordingName);
        return recordingMetadataDir.resolve(
                base32.encodeAsString(filename.getBytes(StandardCharsets.UTF_8)) + ".json");
    }

    private String getJvmId(ConnectionDescriptor connectionDescriptor) throws IOException {
        String targetId = connectionDescriptor.getTargetId();

        String jvmId =
                this.jvmIdMap.computeIfAbsent(
                        targetId,
                        k -> {
                            if (targetId.equals(RecordingArchiveHelper.ARCHIVES)) {
                                return RecordingArchiveHelper.ARCHIVES;
                            }

                            try {
                                ConnectionDescriptor cd = connectionDescriptor;

                                if (connectionDescriptor.getCredentials().isEmpty()) {
                                    cd =
                                            new ConnectionDescriptor(
                                                    targetId,
                                                    credentialsManager.getCredentialsByTargetId(
                                                            targetId));
                                }

                                return this.targetConnectionManager.executeConnectedTask(
                                        cd,
                                        connection -> {
                                            return (String) connection.getJvmId();
                                        });
                            } catch (Exception e) {
                                logger.error(e);
                                return null;
                            }
                        });

        if (jvmId == null) {
            throw new IOException(String.format("Error connecting to target %s", targetId));
        }
        return jvmId;
    }

    private static class StoredRecordingMetadata extends Metadata {
        private final String jvmId;
        private final String recordingName;
        private final String targetId;

        StoredRecordingMetadata(
                String targetId, String jvmId, String recordingName, Map<String, String> labels) {
            super(labels);
            this.targetId = targetId;
            this.jvmId = jvmId;
            this.recordingName = recordingName;
        }

        static StoredRecordingMetadata of(
                String targetId, String jvmId, String recordingName, Metadata metadata) {
            return new StoredRecordingMetadata(
                    targetId, jvmId, recordingName, metadata.getLabels());
        }

        String getTargetId() {
            return this.targetId;
        }

        String getJvmId() {
            return this.jvmId;
        }

        String getRecordingName() {
            return this.recordingName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof StoredRecordingMetadata)) {
                return false;
            }

            StoredRecordingMetadata srm = (StoredRecordingMetadata) o;
            return new EqualsBuilder()
                    .appendSuper(super.equals(o))
                    .append(targetId, srm.targetId)
                    .append(jvmId, srm.jvmId)
                    .append(recordingName, srm.recordingName)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
                    .append(targetId)
                    .append(jvmId)
                    .append(recordingName)
                    .toHashCode();
        }
    }

    public static class Metadata {
        protected final Map<String, String> labels;

        public Metadata() {
            this.labels = new ConcurrentHashMap<>();
        }

        public Metadata(Metadata o) {
            this.labels = new ConcurrentHashMap<>(o.labels);
        }

        public Metadata(Map<String, String> labels) {
            this.labels = new ConcurrentHashMap<>(labels);
        }

        public Map<String, String> getLabels() {
            return new ConcurrentHashMap<>(labels);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof Metadata)) {
                return false;
            }

            Metadata metadata = (Metadata) o;
            return new EqualsBuilder().append(labels, metadata.labels).build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(labels).toHashCode();
        }
    }
}
