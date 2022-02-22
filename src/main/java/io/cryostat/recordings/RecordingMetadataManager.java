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

import java.lang.reflect.Type;
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
import com.google.gson.reflect.TypeToken;
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

    public Future<String> addRecordingLabels(String targetId, String recordingName, String labels)
            throws IllegalArgumentException {
        this.recordingLabelsMap.put(
                Pair.of(targetId, recordingName), validateRecordingLabels(labels));
        return CompletableFuture.completedFuture(labels);
    }

    public Map<String, String> getRecordingLabels(String targetId, String recordingName) {
        String opt =
                Optional.ofNullable(recordingLabelsMap.get(Pair.of(targetId, recordingName)))
                        .orElse("");
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(opt, mapType);
    }

    public String getRecordingLabelsAsString(String targetId, String recordingName) {
        Optional<String> opt =
                Optional.ofNullable(this.recordingLabelsMap.get(Pair.of(targetId, recordingName)));
        return opt.orElse("");
    }

    public void deleteRecordingLabelsIfExists(String targetId, String recordingName) {
        this.recordingLabelsMap.remove(Pair.of(targetId, recordingName));
    }

    private String validateRecordingLabels(String labels) throws IllegalArgumentException {
        if (labels == null) {
            throw new IllegalArgumentException(labels);
        }

        try {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> map = gson.fromJson(labels, mapType);
            if (map == null) {
                throw new IllegalArgumentException(labels);
            }
            return labels;
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
