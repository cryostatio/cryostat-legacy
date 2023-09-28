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

import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import io.cryostat.core.EventOptionsBuilder;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;

import io.vertx.core.Vertx;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class RecordingTargetHelperTest {
    RecordingTargetHelper recordingTargetHelper;
    @Mock Vertx vertx;
    @Mock AuthManager auth;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock WebServer webServer;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock NotificationFactory notificationFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock ReportService reportService;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;

    @Mock JFRConnection connection;
    @Mock CryostatFlightRecorderService service;

    @BeforeEach
    void setup() {
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
        lenient().when(vertx.setTimer(Mockito.anyLong(), Mockito.any())).thenReturn(1234L);
        this.recordingTargetHelper =
                new RecordingTargetHelper(
                        vertx,
                        targetConnectionManager,
                        () -> webServer,
                        eventOptionsBuilderFactory,
                        notificationFactory,
                        recordingOptionsBuilderFactory,
                        reportService,
                        recordingMetadataManager,
                        recordingArchiveHelper,
                        logger);
    }

    @Test
    void shouldGetRecording() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String recordingName = "someRecording";

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        Mockito.when(service.openStream(descriptor, false))
                .thenReturn(new ByteArrayInputStream(src));

        InputStream stream =
                recordingTargetHelper.getRecording(connectionDescriptor, recordingName).get().get();

        Assertions.assertArrayEquals(src, stream.readAllBytes());
    }

    @Test
    void shouldHandleGetWhenRecordingNotFound() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String recordingName = "someRecording";

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor("notSomeRecording");
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        Optional<InputStream> stream =
                recordingTargetHelper.getRecording(connectionDescriptor, recordingName).get();

        Assertions.assertTrue(stream.isEmpty());
    }

    @Test
    void shouldDeleteRecording() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String recordingName = "someRecording";

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        Mockito.when(
                        recordingMetadataManager.deleteRecordingMetadataIfExists(
                                Mockito.any(ConnectionDescriptor.class), Mockito.anyString()))
                .thenReturn(new Metadata());

        recordingTargetHelper.deleteRecording(connectionDescriptor, recordingName).get();

        Mockito.verify(service).close(descriptor);
        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);
        Mockito.verify(reportService)
                .delete(connectionDescriptorCaptor.capture(), Mockito.eq(recordingName));
        MatcherAssert.assertThat(
                connectionDescriptorCaptor.getValue().getTargetId(),
                Matchers.equalTo(connectionDescriptor.getTargetId()));

        Metadata metadata = new Metadata();
        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                new HyperlinkedSerializableRecordingDescriptor(descriptor, null, null, metadata);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingDeleted");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(Map.of("recording", linkedDesc, "target", "fooTarget"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldDeleteSnapshot() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String recordingName = "snapshot-1";

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        Mockito.when(
                        recordingMetadataManager.deleteRecordingMetadataIfExists(
                                Mockito.any(ConnectionDescriptor.class), Mockito.anyString()))
                .thenReturn(new Metadata());

        recordingTargetHelper.deleteRecording(connectionDescriptor, recordingName).get();

        Mockito.verify(service).close(descriptor);
        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);
        Mockito.verify(reportService)
                .delete(connectionDescriptorCaptor.capture(), Mockito.eq(recordingName));
        MatcherAssert.assertThat(
                connectionDescriptorCaptor.getValue().getTargetId(),
                Matchers.equalTo(connectionDescriptor.getTargetId()));

        Metadata metadata = new Metadata();
        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                new HyperlinkedSerializableRecordingDescriptor(descriptor, null, null, metadata);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("SnapshotDeleted");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(Map.of("recording", linkedDesc, "target", "fooTarget"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo",
                "someRecording",
                "my_recording",
                "snapshot",
                "snapshot-",
                "snapshot-h7",
                "56ysnapshot-6",
                "snapshot-43646h",
                "snapshot_-_6",
                "snap-8",
                "shot-22",
                "snpshot-1"
            })
    void shouldCorrectlyDetermineDeletedRecordingIsNotSnapshot(String recordingName)
            throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        Mockito.when(
                        recordingMetadataManager.deleteRecordingMetadataIfExists(
                                Mockito.any(ConnectionDescriptor.class), Mockito.anyString()))
                .thenReturn(new Metadata());

        recordingTargetHelper.deleteRecording(connectionDescriptor, recordingName).get();

        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingDeleted");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "snapshot-0",
                "snapshot-53",
                "snapshot-34598",
                "snapshot-1111111111111111",
            })
    void shouldCorrectlyDetermineDeletedRecordingIsSnapshot(String recordingName) throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        Mockito.when(
                        recordingMetadataManager.deleteRecordingMetadataIfExists(
                                Mockito.any(ConnectionDescriptor.class), Mockito.anyString()))
                .thenReturn(new Metadata());

        recordingTargetHelper.deleteRecording(connectionDescriptor, recordingName).get();

        Mockito.verify(notificationBuilder).metaCategory("SnapshotDeleted");
    }

    @Test
    void shouldHandleDeleteWhenRecordingNotFound() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String recordingName = "someRecording";

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        recordingTargetHelper
                                .deleteRecording(connectionDescriptor, recordingName)
                                .get();
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof RecordingNotFoundException);
                        throw ee;
                    }
                });
    }

    @Test
    void shouldCreateSnapshot() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.eq(connectionDescriptor), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        IRecordingDescriptor recordingDescriptor = createDescriptor("snapshot");
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getSnapshotRecording()).thenReturn(recordingDescriptor);

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(service))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);

        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recordingDescriptor));

        Mockito.when(webServer.getDownloadURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/download");
        Mockito.when(webServer.getReportURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/report");

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        HyperlinkedSerializableRecordingDescriptor result =
                recordingTargetHelper.createSnapshot(connectionDescriptor).get();

        Mockito.verify(service).getSnapshotRecording();
        Mockito.verify(recordingOptionsBuilder).name("snapshot-1");
        Mockito.verify(recordingOptionsBuilder).build();
        Mockito.verify(service).updateRecordingOptions(recordingDescriptor, map);

        HyperlinkedSerializableRecordingDescriptor expected =
                new HyperlinkedSerializableRecordingDescriptor(
                        recordingDescriptor,
                        "http://example.com/download",
                        "http://example.com/report");
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }

    @Test
    void shouldThrowSnapshotCreationExceptionWhenCreationFails() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");

        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.eq(connectionDescriptor), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        IRecordingDescriptor recordingDescriptor = createDescriptor("snapshot");
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getSnapshotRecording()).thenReturn(recordingDescriptor);

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(service))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);

        Mockito.when(service.getAvailableRecordings())
                .thenReturn(new ArrayList<IRecordingDescriptor>());

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        recordingTargetHelper.createSnapshot(connectionDescriptor).get();
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof SnapshotCreationException);
                        throw ee;
                    }
                });
    }

    @Test
    void shouldVerifySnapshotWithNotification() throws Exception {
        RecordingTargetHelper recordingTargetHelperSpy = Mockito.spy(recordingTargetHelper);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String snapshotName = "snapshot-1";
        Future<Optional<InputStream>> future = Mockito.mock(Future.class);
        Mockito.doReturn(future)
                .when(recordingTargetHelperSpy)
                .getRecording(connectionDescriptor, snapshotName);

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(future.get()).thenReturn(snapshotOptional);

        Mockito.when(snapshotOptional.isEmpty()).thenReturn(false);

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        InputStream snapshot = new ByteArrayInputStream(src);
        Mockito.when(snapshotOptional.get()).thenReturn(snapshot);

        Mockito.when(targetConnectionManager.markConnectionInUse(connectionDescriptor))
                .thenReturn(true);

        IRecordingDescriptor minimalDescriptor = createDescriptor(snapshotName);
        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                new HyperlinkedSerializableRecordingDescriptor(
                        minimalDescriptor,
                        "http://example.com/download",
                        "http://example.com/report");

        boolean verified =
                recordingTargetHelperSpy
                        .verifySnapshot(connectionDescriptor, snapshotDescriptor)
                        .get();

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("SnapshotCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(Map.of("recording", snapshotDescriptor, "target", "fooTarget"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        Assertions.assertTrue(verified);
    }

    @Test
    void shouldVerifySnapshotWithoutNotification() throws Exception {
        RecordingTargetHelper recordingTargetHelperSpy = Mockito.spy(recordingTargetHelper);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String snapshotName = "snapshot-1";
        Future<Optional<InputStream>> future = Mockito.mock(Future.class);
        Mockito.doReturn(future)
                .when(recordingTargetHelperSpy)
                .getRecording(connectionDescriptor, snapshotName);

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(future.get()).thenReturn(snapshotOptional);

        Mockito.when(snapshotOptional.isEmpty()).thenReturn(false);

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        InputStream snapshot = new ByteArrayInputStream(src);
        Mockito.when(snapshotOptional.get()).thenReturn(snapshot);

        Mockito.when(targetConnectionManager.markConnectionInUse(connectionDescriptor))
                .thenReturn(true);

        IRecordingDescriptor minimalDescriptor = createDescriptor(snapshotName);
        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                new HyperlinkedSerializableRecordingDescriptor(
                        minimalDescriptor,
                        "http://example.com/download",
                        "http://example.com/report");

        boolean verified =
                recordingTargetHelperSpy
                        .verifySnapshot(connectionDescriptor, snapshotDescriptor, false)
                        .get();

        Mockito.verify(notificationFactory, Mockito.never()).createBuilder();
        Mockito.verify(notificationBuilder, Mockito.never()).metaCategory("SnapshotCreated");
        Mockito.verify(notificationBuilder, Mockito.never()).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder, Mockito.never())
                .message(Map.of("recording", snapshotDescriptor, "target", "fooTarget"));
        Mockito.verify(notificationBuilder, Mockito.never()).build();
        Mockito.verify(notification, Mockito.never()).send();

        Assertions.assertTrue(verified);
    }

    @Test
    void shouldThrowSnapshotCreationExceptionWhenVerificationFails() throws Exception {
        RecordingTargetHelper recordingTargetHelperSpy = Mockito.spy(recordingTargetHelper);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String snapshotName = "snapshot-1";
        Future<Optional<InputStream>> future = Mockito.mock(Future.class);
        Mockito.doReturn(future)
                .when(recordingTargetHelperSpy)
                .getRecording(connectionDescriptor, snapshotName);

        Optional<InputStream> snapshotOptional = Optional.empty();
        Mockito.when(future.get()).thenReturn(snapshotOptional);

        IRecordingDescriptor minimalDescriptor = createDescriptor(snapshotName);
        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                new HyperlinkedSerializableRecordingDescriptor(
                        minimalDescriptor,
                        "http://example.com/download",
                        "http://example.com/report");

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        recordingTargetHelperSpy
                                .verifySnapshot(connectionDescriptor, snapshotDescriptor)
                                .get();
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof SnapshotCreationException);
                        throw ee;
                    }
                });
    }

    @Test
    void shouldHandleVerificationWhenSnapshotNotReadable() throws Exception {
        RecordingTargetHelper recordingTargetHelperSpy = Mockito.spy(recordingTargetHelper);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        String snapshotName = "snapshot-1";

        Future<Optional<InputStream>> getFuture = Mockito.mock(Future.class);
        Mockito.doReturn(getFuture)
                .when(recordingTargetHelperSpy)
                .getRecording(connectionDescriptor, snapshotName);

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(getFuture.get()).thenReturn(snapshotOptional);

        Mockito.when(snapshotOptional.isEmpty()).thenReturn(false);

        InputStream snapshot = Mockito.mock(InputStream.class);
        Mockito.when(snapshotOptional.get()).thenReturn(snapshot);

        Mockito.when(targetConnectionManager.markConnectionInUse(connectionDescriptor))
                .thenReturn(true);
        Mockito.doThrow(IOException.class).when(snapshot).read();

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor(snapshotName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        IRecordingDescriptor minimalDescriptor = createDescriptor(snapshotName);
        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                new HyperlinkedSerializableRecordingDescriptor(
                        minimalDescriptor,
                        "http://example.com/download",
                        "http://example.com/report");

        boolean verified =
                recordingTargetHelperSpy
                        .verifySnapshot(connectionDescriptor, snapshotDescriptor)
                        .get();

        Assertions.assertFalse(verified);
    }

    @Test
    void shouldStartRecordingWithFixedDuration() throws Exception {
        String recordingName = "someRecording";
        String targetId = "fooTarget";
        String duration = "3000ms";
        String templateName = "Profiling";
        TemplateType templateType = TemplateType.TARGET;
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        IRecordingDescriptor recordingDescriptor = createDescriptor(recordingName);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Metadata metadata =
                new Metadata(Map.of("template.name", "Profiling", "template.type", "TARGET"));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });

        Mockito.when(recordingOptions.get(Mockito.any())).thenReturn(recordingName, duration);

        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings())
                .thenReturn(Collections.emptyList(), List.of(recordingDescriptor));

        Mockito.when(
                        service.start(
                                Mockito.any(), Mockito.eq(templateName), Mockito.eq(templateType)))
                .thenReturn(recordingDescriptor);

        Mockito.when(
                        recordingMetadataManager.setRecordingMetadata(
                                Mockito.any(), Mockito.anyString(), Mockito.any(Metadata.class)))
                .thenAnswer(
                        new Answer<Future<Metadata>>() {
                            @Override
                            public Future<Metadata> answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return CompletableFuture.completedFuture(invocation.getArgument(2));
                            }
                        });

        recordingTargetHelper.startRecording(
                ReplacementPolicy.NEVER,
                connectionDescriptor,
                recordingOptions,
                templateName,
                templateType,
                metadata,
                false);

        Mockito.verify(service)
                .start(Mockito.any(), Mockito.eq(templateName), Mockito.eq(templateType));

        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                new HyperlinkedSerializableRecordingDescriptor(recordingDescriptor, null, null);

        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        Map message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.get("target"), Matchers.equalTo(targetId));

        HyperlinkedSerializableRecordingDescriptor capturedDescriptor =
                (HyperlinkedSerializableRecordingDescriptor) message.get("recording");
        MatcherAssert.assertThat(
                capturedDescriptor.getName(), Matchers.equalTo(linkedDesc.getName()));
        MatcherAssert.assertThat(
                capturedDescriptor.getReportUrl(), Matchers.equalTo(linkedDesc.getReportUrl()));
        MatcherAssert.assertThat(
                capturedDescriptor.getDownloadUrl(), Matchers.equalTo(linkedDesc.getDownloadUrl()));
        MatcherAssert.assertThat(
                capturedDescriptor.getState(), Matchers.equalTo(linkedDesc.getState()));
        MatcherAssert.assertThat(
                capturedDescriptor.getDuration(), Matchers.equalTo(linkedDesc.getDuration()));
        MatcherAssert.assertThat(capturedDescriptor.getId(), Matchers.equalTo(linkedDesc.getId()));
        MatcherAssert.assertThat(
                capturedDescriptor.getMaxAge(), Matchers.equalTo(linkedDesc.getMaxAge()));
        MatcherAssert.assertThat(
                capturedDescriptor.getMaxSize(), Matchers.equalTo(linkedDesc.getMaxSize()));
        MatcherAssert.assertThat(
                capturedDescriptor.getStartTime(), Matchers.equalTo(linkedDesc.getStartTime()));
        MatcherAssert.assertThat(
                capturedDescriptor.getToDisk(), Matchers.equalTo(linkedDesc.getToDisk()));

        MatcherAssert.assertThat(
                capturedDescriptor.getMetadata(),
                Matchers.equalTo(
                        new Metadata(
                                Map.of("template.name", "Profiling", "template.type", "TARGET"))));
    }

    void shouldReplaceExistingRecording() throws Exception {
        String recordingName = "existingRecording";
        String targetId = "fooTarget";
        String duration = "5000ms";
        String templateName = "Profiling";
        TemplateType templateType = TemplateType.TARGET;
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        IRecordingDescriptor existingRecording = createDescriptor(recordingName);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Metadata metadata =
                new Metadata(Map.of("template.name", "Profiling", "template.type", "TARGET"));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        invocation -> {
                            TargetConnectionManager.ConnectedTask task = invocation.getArgument(1);
                            return task.execute(connection);
                        });

        Mockito.when(recordingOptions.get(Mockito.any())).thenReturn(recordingName, duration);

        Mockito.when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> existingRecordings = List.of(existingRecording);
        Mockito.when(service.getAvailableRecordings()).thenReturn(existingRecordings);

        Mockito.when(service.start(Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(existingRecording);

        Mockito.when(existingRecording.getState()).thenReturn(RecordingState.STOPPED);

        TemplateService templateService = Mockito.mock(TemplateService.class);
        IConstrainedMap<EventOptionID> events = Mockito.mock(IConstrainedMap.class);
        Mockito.when(connection.getTemplateService()).thenReturn(templateService);
        Mockito.when(templateService.getEvents(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.of(events));

        Mockito.when(
                        recordingMetadataManager.setRecordingMetadata(
                                Mockito.any(), Mockito.anyString(), Mockito.any(Metadata.class)))
                .thenAnswer(
                        invocation -> {
                            return CompletableFuture.completedFuture(invocation.getArgument(2));
                        });

        Mockito.doNothing().when(notification).send();

        recordingTargetHelper.startRecording(
                ReplacementPolicy.ALWAYS,
                connectionDescriptor,
                recordingOptions,
                templateName,
                templateType,
                metadata,
                false);

        Mockito.verify(service).close(existingRecording);
        Mockito.verify(service).start(Mockito.any(), Mockito.anyString(), Mockito.any());

        // Verify notification not sent because recording exists and no new recording is created
        Mockito.verify(notification, Mockito.times(0)).send();
    }

    @Test
    void shouldCloseAndRecreateIfRecordingExistsAndIsRunning() throws Exception {
        String recordingName = "existingRecording";
        String targetId = "fooTarget";
        String duration = "5000ms";
        String templateName = "Profiling";
        TemplateType templateType = TemplateType.TARGET;
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        IRecordingDescriptor existingRecording = createDescriptor(recordingName);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Metadata metadata =
                new Metadata(Map.of("template.name", "Profiling", "template.type", "TARGET"));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        invocation -> {
                            TargetConnectionManager.ConnectedTask task = invocation.getArgument(1);
                            return task.execute(connection);
                        });

        Mockito.when(recordingOptions.get(Mockito.any())).thenReturn(recordingName, duration);

        Mockito.when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> existingRecordings = List.of(existingRecording);
        Mockito.when(service.getAvailableRecordings()).thenReturn(existingRecordings);

        Mockito.when(service.start(Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(existingRecording);

        Mockito.when(existingRecording.getState()).thenReturn(RecordingState.RUNNING);

        Mockito.when(
                        recordingMetadataManager.setRecordingMetadata(
                                Mockito.any(), Mockito.anyString(), Mockito.any(Metadata.class)))
                .thenAnswer(
                        invocation -> {
                            return CompletableFuture.completedFuture(invocation.getArgument(2));
                        });

        Mockito.doNothing().when(notification).send();

        recordingTargetHelper.startRecording(
                ReplacementPolicy.ALWAYS,
                connectionDescriptor,
                recordingOptions,
                templateName,
                templateType,
                metadata,
                false);

        Mockito.verify(service).close(existingRecording);
        Mockito.verify(service).start(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldRestartRecordingWhenRecordingExistsAndIsStopped() throws Exception {
        String recordingName = "existingRecording";
        String targetId = "fooTarget";
        String duration = "5000ms";
        String templateName = "Profiling";
        TemplateType templateType = TemplateType.TARGET;
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        IRecordingDescriptor existingRecording = createDescriptor(recordingName);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        Metadata metadata =
                new Metadata(Map.of("template.name", "Profiling", "template.type", "TARGET"));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        invocation -> {
                            TargetConnectionManager.ConnectedTask task = invocation.getArgument(1);
                            return task.execute(connection);
                        });

        Mockito.when(recordingOptions.get(Mockito.any())).thenReturn(recordingName, duration);

        Mockito.when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> existingRecordings = List.of(existingRecording);
        Mockito.when(service.getAvailableRecordings()).thenReturn(existingRecordings);

        Mockito.when(service.start(Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(existingRecording);

        Mockito.when(existingRecording.getState()).thenReturn(RecordingState.STOPPED);

        Mockito.when(
                        recordingMetadataManager.setRecordingMetadata(
                                Mockito.any(), Mockito.anyString(), Mockito.any(Metadata.class)))
                .thenAnswer(
                        invocation -> {
                            return CompletableFuture.completedFuture(invocation.getArgument(2));
                        });

        Mockito.doNothing().when(notification).send();

        recordingTargetHelper.startRecording(
                ReplacementPolicy.STOPPED,
                connectionDescriptor,
                recordingOptions,
                templateName,
                templateType,
                metadata,
                false);

        Mockito.verify(service).close(existingRecording);
        Mockito.verify(service).start(Mockito.any(), Mockito.anyString(), Mockito.any());
        Mockito.verify(notification).send();
        Mockito.verifyNoMoreInteractions(recordingOptions);
    }

    @Test
    void shouldStopRecording() throws Exception {
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(descriptor.getName()).thenReturn("someRecording");
        Mockito.when(descriptor.getState()).thenReturn(RecordingState.RUNNING);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        recordingTargetHelper.stopRecording(new ConnectionDescriptor("fooTarget"), "someRecording");

        Mockito.verify(service).stop(descriptor);

        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                new HyperlinkedSerializableRecordingDescriptor(
                        descriptor, null, null, RecordingState.STOPPED);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingStopped");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(Map.of("recording", linkedDesc, "target", "fooTarget"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldThrowWhenStopRecordingNotFound() throws Exception {
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        RecordingNotFoundException rnfe =
                Assertions.assertThrows(
                        RecordingNotFoundException.class,
                        () ->
                                recordingTargetHelper.stopRecording(
                                        new ConnectionDescriptor("fooTarget"), "someRecording"));
        MatcherAssert.assertThat(
                rnfe.getMessage(),
                Matchers.equalTo(
                        "Recording someRecording was not found in the target [fooTarget]."));
    }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
        IQuantity zeroQuantity = Mockito.mock(IQuantity.class);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.lenient().when(descriptor.getId()).thenReturn(1L);
        Mockito.lenient().when(descriptor.getName()).thenReturn(name).thenReturn(name + "-1");
        Mockito.lenient()
                .when(descriptor.getState())
                .thenReturn(IRecordingDescriptor.RecordingState.STOPPED);
        Mockito.lenient().when(descriptor.getStartTime()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getDuration()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.isContinuous()).thenReturn(false);
        Mockito.lenient().when(descriptor.getToDisk()).thenReturn(false);
        Mockito.lenient().when(descriptor.getMaxSize()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getMaxAge()).thenReturn(zeroQuantity);
        return descriptor;
    }
}
