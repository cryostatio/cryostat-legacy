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

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;

import com.google.gson.Gson;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

public class RecordingMetadataManagerTest {

    RecordingMetadataManager recordingMetadataManager;
    @Mock Path recordingMetadataDir;
    @Mock FileSystem fs;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        Gson gson = new Gson();
        this.recordingMetadataManager =
                new RecordingMetadataManager(recordingMetadataDir, fs, gson, logger);
    }

    @Test
    void shouldParseAndStoreLabelsInRecordingLabelsMap() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        String labels = "{\"KEY\":\"VALUE\",\"key.2\":\"some.value\",\"key3\":\"1234\"}";

        recordingMetadataManager.addRecordingLabels(targetId, recordingName, labels).get();

        Map<String, String> expectedLabelsMap =
                Map.of(
                        "KEY", "VALUE",
                        "key.2", "some.value",
                        "key3", "1234");
        String expectedLabelsAsString = labels;

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getRecordingLabels(targetId, recordingName);
        String actualLabelsMapAsString =
                recordingMetadataManager.getRecordingLabelsAsString(targetId, recordingName);

        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(expectedLabelsMap));
        MatcherAssert.assertThat(actualLabelsMapAsString, Matchers.equalTo(expectedLabelsAsString));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "12345",
                "thisIsNotJson",
            })
    void shouldThrowOnInvalidLabels(String labels) throws Exception {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        recordingMetadataManager.addRecordingLabels(
                                "someTarget", "someRecording", labels));
    }

    @Test
    void shouldDeleteLabels() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";

        recordingMetadataManager
                .addRecordingLabels(targetId, recordingName, "{\"key\":\"value\"}")
                .get();

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getRecordingLabels(targetId, recordingName);
        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(Map.of("key", "value")));

        recordingMetadataManager.deleteRecordingLabelsIfExists(targetId, recordingName);

        MatcherAssert.assertThat(recordingMetadataManager.getRecordingLabelsAsString(targetId, recordingName), Matchers.equalTo(""));
    }

    @Test
    void shouldUpdateLabelsForExistingLabelEntries() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        String labels = "{\"KEY\":\"VALUE\",\"key.2\":\"some.value\",\"key3\":\"1234\"}";
        String updatedLabels = "{\"key.2\":\"updated.value\",\"key4\":\"NEWVALUE\"}";

        recordingMetadataManager.addRecordingLabels(targetId, recordingName, labels).get();

        recordingMetadataManager
                .updateRecordingLabels(targetId, recordingName, updatedLabels)
                .get();

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getRecordingLabels(targetId, recordingName);

        Map<String, String> expectedLabelsMap =
                Map.of(
                        "KEY", "VALUE",
                        "key.2", "updated.value",
                        "key3", "1234",
                        "key4", "NEWVALUE");

        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(expectedLabelsMap));
    }

    @Test
    void shouldUpdateLabelsWhenLabelsNotPreviouslyCreated() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        String labels = "{\"KEY\":\"VALUE\",\"key.2\":\"some.value\",\"key3\":\"1234\"}";

        recordingMetadataManager.updateRecordingLabels(targetId, recordingName, labels).get();

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getRecordingLabels(targetId, recordingName);

        Map<String, String> expectedLabelsMap =
                Map.of(
                        "KEY", "VALUE",
                        "key.2", "some.value",
                        "key3", "1234");

        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(expectedLabelsMap));
    }
}
