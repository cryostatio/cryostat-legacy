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

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Map;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecordingMetadataManagerTest {

    RecordingMetadataManager recordingMetadataManager;
    @Mock Path recordingMetadataDir;
    @Mock FileSystem fs;
    @Mock Base32 base32;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        Gson gson = new Gson();
        Base32 base32 = new Base32();

        this.recordingMetadataManager =
                new RecordingMetadataManager(recordingMetadataDir, fs, gson, base32, logger);
    }

    @Test
    void shouldParseAndStoreLabelsInRecordingLabelsMap() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        Map<String, String> labels =
                Map.of("KEY", "newValue", "key.2", "some.value", "key3", "1234");

        Path mockPath = Mockito.mock(Path.class);
        Mockito.when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(targetId, recordingName, new Metadata(labels))
                .get();

        Mockito.verify(fs)
                .writeString(
                        Mockito.any(Path.class),
                        Mockito.anyString(),
                        Mockito.any(OpenOption.class),
                        Mockito.any(OpenOption.class),
                        Mockito.any(OpenOption.class));

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getMetadata(targetId, recordingName).getLabels();

        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(labels));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "12345",
                "thisIsNotJson",
                "{\"this label\":\"contains whitespace\"}",
                "{\"thislabel\":\"contains\twhitespace\"}",
            })
    void shouldThrowOnInvalidLabels(String labels) throws Exception {
        Class<? extends Exception> expected;
        if (labels == null) {
            expected = NullPointerException.class;
        } else {
            expected = IllegalArgumentException.class;
        }
        Assertions.assertThrows(
                expected, () -> recordingMetadataManager.parseRecordingLabels(labels));
    }

    @Test
    void shouldDeleteLabels() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        Map<String, String> labels =
                Map.of("KEY", "newValue", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);

        Path mockPath = Mockito.mock(Path.class);
        Mockito.when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        recordingMetadataManager.setRecordingMetadata(targetId, recordingName, metadata).get();

        Map<String, String> actualLabelsMap =
                recordingMetadataManager.getMetadata(targetId, recordingName).getLabels();
        MatcherAssert.assertThat(actualLabelsMap, Matchers.equalTo(labels));

        recordingMetadataManager.deleteRecordingMetadataIfExists(targetId, recordingName);

        MatcherAssert.assertThat(
                recordingMetadataManager.getMetadata(targetId, recordingName).getLabels(),
                Matchers.equalTo(Map.of()));
        Mockito.verify(fs).deleteIfExists(Mockito.any(Path.class));
    }

    @Test
    void shouldOverwriteLabelsForExistingLabelEntries() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        Map<String, String> updatedLabels =
                Map.of("KEY", "UPDATED_VALUE", "key.2", "some.value", "key3", "1234");
        Metadata updatedMetadata = new Metadata(updatedLabels);

        Path mockPath = Mockito.mock(Path.class);
        Mockito.when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager.setRecordingMetadata(targetId, recordingName, metadata).get();

        recordingMetadataManager
                .setRecordingMetadata(targetId, recordingName, updatedMetadata)
                .get();

        Metadata actualMetadata = recordingMetadataManager.getMetadata(targetId, recordingName);

        MatcherAssert.assertThat(actualMetadata, Matchers.equalTo(updatedMetadata));
    }

    @Test
    void shouldCopyLabelsToArchivedRecordings() throws Exception {
        String targetId = "someTarget";
        String recordingName = "someRecording";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        String filename = "archivedRecording";

        recordingMetadataManager.setRecordingMetadata(targetId, recordingName, metadata).get();

        recordingMetadataManager.copyMetadataToArchives(targetId, recordingName, filename);

        Metadata actualMetadata =
<<<<<<< HEAD
                recordingMetadataManager.getMetadata(RecordingArchiveHelper.ARCHIVES, filename);
=======
                recordingMetadataManager.getMetadata(targetId, filename);
>>>>>>> 5c7883b8 (use runtime info hash instead of guid)

        MatcherAssert.assertThat(actualMetadata, Matchers.equalTo(metadata));
    }
}
