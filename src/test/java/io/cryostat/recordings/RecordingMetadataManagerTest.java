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
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
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

    @Mock AuthManager auth;
    @Mock Path recordingMetadataDir;
    @Mock Path archivedRecordingsPath;
    @Mock FileSystem fs;
    @Mock Provider<RecordingArchiveHelper> archiveHelperProvider;
    @Mock Base32 base32;
    @Mock Logger logger;
    @Mock DiscoveryStorage storage;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock CredentialsManager credentialsManager;
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
                        auth,
                        new DirectExecutorService(),
                        recordingMetadataDir,
                        archivedRecordingsPath,
                        30,
                        fs,
                        archiveHelperProvider,
                        targetConnectionManager,
                        credentialsManager,
                        storage,
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
                .setRecordingMetadata(connectionDescriptor, recordingName, labels)
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
        Metadata metadata = new Metadata(SecurityContext.DEFAULT, labels);

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
                .setRecordingMetadata(connectionDescriptor, recordingName, labels)
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
        Metadata metadata = new Metadata(SecurityContext.DEFAULT, labels);
        StoredRecordingMetadata srm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, metadata);
        Map<String, String> updatedLabels =
                Map.of("KEY", "UPDATED_VALUE", "key.2", "some.value", "key3", "1234");
        Metadata updatedMetadata = new Metadata(SecurityContext.DEFAULT, updatedLabels);
        StoredRecordingMetadata updatedSrm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, updatedMetadata);

        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn(targetId);
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, labels)
                .get();

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, updatedLabels)
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
        Metadata metadata = new Metadata(SecurityContext.DEFAULT, labels);
        StoredRecordingMetadata srm =
                StoredRecordingMetadata.of(targetId, jvmId, recordingName, metadata);
        String filename = "archivedRecording";
        when(jvmIdHelper.getJvmId(Mockito.any(ConnectionDescriptor.class))).thenReturn(jvmId);
        when(connectionDescriptor.getTargetId()).thenReturn(targetId);
        Path mockPath = Mockito.mock(Path.class);
        when(recordingMetadataDir.resolve(Mockito.anyString())).thenReturn(mockPath);
        when(mockPath.resolve(Mockito.anyString())).thenReturn(mockPath);

        recordingMetadataManager
                .setRecordingMetadata(connectionDescriptor, recordingName, labels)
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
