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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Map;

import javax.inject.Provider;

import io.cryostat.DirectExecutorService;
import io.cryostat.MockVertx;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import com.google.gson.Gson;
import io.vertx.core.Vertx;
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
    Vertx vertx = MockVertx.vertx();

    @Mock Path recordingMetadataDir;
    @Mock Path archivedRecordingsPath;
    @Mock FileSystem fs;
    @Mock Provider<RecordingArchiveHelper> archiveHelperProvider;
    @Mock Base32 base32;
    @Mock Logger logger;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock CredentialsManager credentialsManager;
    @Mock PlatformClient platformClient;
    @Mock NotificationFactory notificationFactory;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock JFRConnection connection;
    @Mock ConnectionDescriptor connectionDescriptor;

    @BeforeEach
    void setup() {
        Gson gson = new Gson();
        Base32 base32 = new Base32();

        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);

        this.recordingMetadataManager =
                new RecordingMetadataManager(
                        new DirectExecutorService(),
                        recordingMetadataDir,
                        archivedRecordingsPath,
                        30,
                        fs,
                        archiveHelperProvider,
                        targetConnectionManager,
                        credentialsManager,
                        platformClient,
                        notificationFactory,
                        jvmIdHelper,
                        gson,
                        base32,
                        logger);
        this.recordingMetadataManager.init(vertx, null);
    }

    @Test
    void shouldParseAndStoreLabelsInRecordingLabelsMap() throws Exception {
        String recordingName = "someRecording";
        String jvmId = "id";

        Map<String, String> labels =
                Map.of("KEY", "newValue", "key.2", "some.value", "key3", "1234");

        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn("someTarget");
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, new Metadata(labels))
                .get();

        verify(fs)
                .writeString(
                        Mockito.any(Path.class),
                        Mockito.anyString(),
                        Mockito.any(OpenOption.class),
                        Mockito.any(OpenOption.class),
                        Mockito.any(OpenOption.class));

        MatcherAssert.assertThat(
                recordingMetadataManager
                        .getMetadata(connectionDescriptor, recordingName)
                        .getLabels(),
                Matchers.equalTo(labels));
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
        String recordingName = "someRecording";
        String jvmId = "id";
        Map<String, String> labels =
                Map.of("KEY", "newValue", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);

        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn("someTarget");
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        MatcherAssert.assertThat(
                recordingMetadataManager
                        .getMetadata(connectionDescriptor, recordingName)
                        .getLabels(),
                Matchers.equalTo(labels));

        recordingMetadataManager.deleteRecordingMetadataIfExists(
                connectionDescriptor, recordingName);

        MatcherAssert.assertThat(
                recordingMetadataManager
                        .getMetadata(connectionDescriptor, recordingName)
                        .getLabels(),
                Matchers.equalTo(Map.of()));
        verify(fs).deleteIfExists(Mockito.any(Path.class));
    }

    @Test
    void shouldOverwriteLabelsForExistingLabelEntries() throws Exception {
        String recordingName = "someRecording";
        String jvmId = "id";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        Map<String, String> updatedLabels =
                Map.of("KEY", "UPDATED_VALUE", "key.2", "some.value", "key3", "1234");
        Metadata updatedMetadata = new Metadata(updatedLabels);

        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn("someTarget");
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, updatedMetadata)
                .get();

        Metadata actualMetadata =
                recordingMetadataManager.getMetadata(connectionDescriptor, recordingName);

        MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(updatedLabels));
    }

    @Test
    void shouldCopyLabelsToArchivedRecordings() throws Exception {
        String recordingName = "someRecording";
        String jvmId = "id";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        String filename = "archivedRecording";
        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn("someTarget");
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        recordingMetadataManager.copyMetadataToArchives(
                connectionDescriptor, recordingName, filename);

        Metadata actualMetadata =
                recordingMetadataManager.getMetadata(connectionDescriptor, filename);

        MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(labels));
    }
}
