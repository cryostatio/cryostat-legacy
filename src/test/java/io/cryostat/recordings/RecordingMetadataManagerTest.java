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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import javax.inject.Provider;

import io.cryostat.DirectExecutorService;
import io.cryostat.MainModule;
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
import io.cryostat.recordings.RecordingMetadataManager.StoredRecordingMetadata;

import com.google.gson.Gson;
import io.vertx.core.Vertx;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

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
    Gson gson = MainModule.provideGson(logger);

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
        lenient()
                .when(jvmIdHelper.jvmIdToSubdirectoryName(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArgument(0);
                            }
                        });

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
    void shouldParseAndStoreLabels() throws Exception {
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
        Path parentPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.getParent()).thenReturn(parentPath);

        when(fs.deleteIfExists(mockPath)).thenReturn(true);
        when(fs.isRegularFile(mockPath)).thenReturn(true);
        when(fs.readFile(mockPath))
                .thenReturn(new BufferedReader(new StringReader("{\"labels\":{}}")));

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        recordingMetadataManager.deleteRecordingMetadataIfExists(jvmId, recordingName);

        verify(fs).deleteIfExists(Mockito.any(Path.class));
    }

    @Test
    void shouldOverwriteLabelsForExistingLabelEntries() throws Exception {
        String recordingName = "someRecording";
        String targetId = "someTarget";
        String jvmId = "id";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        StoredRecordingMetadata srm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, metadata);
        Map<String, String> updatedLabels =
                Map.of("KEY", "UPDATED_VALUE", "key.2", "some.value", "key3", "1234");
        Metadata updatedMetadata = new Metadata(updatedLabels);
        StoredRecordingMetadata updatedSrm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, updatedMetadata);

        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn(targetId);
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, updatedMetadata)
                .get();

        InOrder inOrder = Mockito.inOrder(fs);
        inOrder.verify(fs)
                .writeString(
                        mockPath,
                        gson.toJson(srm),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
        inOrder.verify(fs)
                .writeString(
                        mockPath,
                        gson.toJson(updatedSrm),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Test
    void shouldCopyLabelsToArchivedRecordings() throws Exception {
        String recordingName = "someRecording";
        String targetId = "someTarget";
        String jvmId = "id";
        Map<String, String> labels = Map.of("KEY", "value", "key.2", "some.value", "key3", "1234");
        Metadata metadata = new Metadata(labels);
        StoredRecordingMetadata srm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, metadata);
        String filename = "archivedRecording";
        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn(targetId);
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, metadata)
                .get();

        recordingMetadataManager.copyMetadataToArchives(
                connectionDescriptor, recordingName, filename);

        Mockito.verify(fs)
                .writeString(
                        mockPath,
                        gson.toJson(srm),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
    }
}
