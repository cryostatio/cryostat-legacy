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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingMetadataManager {

    private final Path recordingMetadataDir;
    private final FileSystem fs;
    private final Gson gson;
    private final Logger logger;

    private final Map<Pair<String, String>, String> recordingLabelsMap;

    RecordingMetadataManager(Path recordingMetadataDir, FileSystem fs, Gson gson, Logger logger) {
        this.recordingMetadataDir = recordingMetadataDir;
        this.fs = fs;
        this.gson = gson;
        this.logger = logger;
        this.recordingLabelsMap = new ConcurrentHashMap<>();
    }

    public Future<Void> addRecordingLabels(String targetId, String recordingName, String labels)
            throws IllegalArgumentException {

        validatedRecordingLabels(labels);

        this.recordingLabelsMap.put(Pair.of(targetId, recordingName), new String(labels));

        return CompletableFuture.completedFuture(null);
    }

    public Future<Void> updateRecordingLabels(
            String targetId, String recordingName, String newLabels)
            throws IllegalArgumentException {
        Pair<String, String> key = Pair.of(targetId, recordingName);

        Optional<String> existingLabels = Optional.ofNullable(this.recordingLabelsMap.get(key));

        Map<String, String> existingMap =
                existingLabels
                        .map(l -> validatedRecordingLabels(l))
                        .orElse(new ConcurrentHashMap<>());
        Map<String, String> newMap = validatedRecordingLabels(newLabels);

        existingMap.putAll(newMap);

        this.recordingLabelsMap.put(key, gson.toJson(existingMap, Map.class));

        return CompletableFuture.completedFuture(null);
    }

    public Map<String, String> getRecordingLabels(String targetId, String recordingName) {
        Optional<String> opt =
                Optional.ofNullable(recordingLabelsMap.get(Pair.of(targetId, recordingName)));
        return opt.map((labels) -> validatedRecordingLabels(labels))
                .orElse(new ConcurrentHashMap<>());
    }

    public String getRecordingLabelsAsString(String targetId, String recordingName) {
        Optional<String> opt =
                Optional.ofNullable(this.recordingLabelsMap.get(Pair.of(targetId, recordingName)));
        return opt.orElse("");
    }

    public void deleteRecordingLabelsIfExists(String targetId, String recordingName) {
        this.recordingLabelsMap.remove(Pair.of(targetId, recordingName));
    }

    private Map<String, String> validatedRecordingLabels(String labels)
            throws IllegalArgumentException {
        if (labels == null) {
            throw new IllegalArgumentException(labels);
        }

        try {
            return gson.fromJson(labels, Map.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static class StoredRecordingMetadata {
        private final String targetId;
        private final String recordingName;
        private final Map<String, String> labels;

        StoredRecordingMetadata(String targetId, String recordingName, Map<String, String> labels) {
            this.targetId = targetId;
            this.recordingName = recordingName;
            this.labels = labels;
        }

        public String getTargetId() {
            return this.targetId;
        }

        public String getRecordingName() {
            return this.recordingName;
        }

        public Map<String, String> getLabels() {
            return this.labels;
        }
    }
}
