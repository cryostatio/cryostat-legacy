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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingMetadataManager {

    public static final String NOTIFICATION_CATEGORY = "RecordingMetadataUpdated";
    private static final Integer UPLOADS_ID = -1;

    private final Path recordingMetadataDir;
    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final Map<Pair<Integer, String>, Metadata> recordingMetadataMap;
    private final Map<String, Integer> jvmIdMap;

    RecordingMetadataManager(
            Path recordingMetadataDir,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            Gson gson,
            Base32 base32,
            Logger logger) {
        this.recordingMetadataDir = recordingMetadataDir;
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
        this.recordingMetadataMap = new ConcurrentHashMap<>();
        this.jvmIdMap = new ConcurrentHashMap<>();
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
                        srm ->
                                recordingMetadataMap.put(
                                        Pair.of(srm.getJvmId(), srm.getRecordingName()), srm));
    }

    public Future<Metadata> setRecordingMetadata(
            String targetId, String recordingName, Metadata metadata) throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);

        Integer jvmId = this.getJvmId(targetId);

        this.recordingMetadataMap.put(Pair.of(jvmId, recordingName), metadata);
        fs.writeString(
                this.getMetadataPath(jvmId, recordingName),
                gson.toJson(StoredRecordingMetadata.of(jvmId, recordingName, metadata)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return CompletableFuture.completedFuture(metadata);
    }

    public Future<Metadata> setRecordingMetadata(String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return this.setRecordingMetadata(RecordingArchiveHelper.ARCHIVES, recordingName, metadata);
    }

    public Metadata getMetadata(String targetId, String recordingName) throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);

        Integer jvmId = this.getJvmId(targetId);
        return this.recordingMetadataMap.computeIfAbsent(
                Pair.of(jvmId, recordingName), k -> new Metadata());
    }

    public Metadata deleteRecordingMetadataIfExists(String targetId, String recordingName)
            throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Integer jvmId = this.getJvmId(targetId);

        Metadata deleted = this.recordingMetadataMap.remove(Pair.of(jvmId, recordingName));
        fs.deleteIfExists(this.getMetadataPath(jvmId, recordingName));
        return deleted;
    }

    public Future<Metadata> copyMetadataToArchives(
            String targetId, String recordingName, String filename) throws IOException {
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(filename);
        Metadata metadata = this.getMetadata(targetId, recordingName);
        return this.setRecordingMetadata(filename, metadata);
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

    private Path getMetadataPath(Integer jvmId, String recordingName) {
        String filename = String.format("%s%s", jvmId, recordingName);
        return recordingMetadataDir.resolve(
                base32.encodeAsString(filename.getBytes(StandardCharsets.UTF_8)) + ".json");
    }

    private Integer getJvmId(String targetId) throws IOException {
        Integer jvmId =
                this.jvmIdMap.computeIfAbsent(
                        targetId,
                        k -> {
                            if (targetId.equals(RecordingArchiveHelper.UNLABELLED)) {
                                return UPLOADS_ID;
                            }

                            try {
                                Credentials credentials =
                                        credentialsManager.getCredentials(targetId);
                                return this.targetConnectionManager.executeConnectedTask(
                                        new ConnectionDescriptor(targetId, credentials),
                                        connection -> {
                                            return connection.getJvmId();
                                        });
                            } catch (Exception e) {
                                logger.error(e);
                                return 0;
                            }
                        });
        if (jvmId == 0) {
            this.jvmIdMap.remove(targetId);
            throw new IOException(String.format("Unable to connect to target %s", targetId));
        }
        return jvmId;
    }

    private static class StoredRecordingMetadata extends Metadata {
        private final Integer jvmId;
        private final String recordingName;

        StoredRecordingMetadata(Integer jvmId, String recordingName, Map<String, String> labels) {
            super(labels);
            this.jvmId = jvmId;
            this.recordingName = recordingName;
        }

        static StoredRecordingMetadata of(Integer jvmId, String recordingName, Metadata metadata) {
            return new StoredRecordingMetadata(jvmId, recordingName, metadata.getLabels());
        }

        Integer getJvmId() {
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
                    .append(jvmId, srm.jvmId)
                    .append(recordingName, srm.recordingName)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
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
