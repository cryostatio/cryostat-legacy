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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.web.http.HttpMimeType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class RecordingTargetHelperTest {
    RecordingTargetHelper recordingTargetHelper;
    @Mock AuthManager auth;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock NotificationFactory notificationFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock ReportService reportService;
    @Mock Logger logger;

    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

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
        this.recordingTargetHelper =
                new RecordingTargetHelper(
                        targetConnectionManager,
                        eventOptionsBuilderFactory,
                        notificationFactory,
                        recordingOptionsBuilderFactory,
                        reportService,
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

        recordingTargetHelper.deleteRecording(connectionDescriptor, recordingName).get();

        Mockito.verify(service).close(descriptor);
        Mockito.verify(reportService)
                .delete(
                        Mockito.argThat(
                                arg ->
                                        arg.getTargetId()
                                                .equals(connectionDescriptor.getTargetId())),
                        Mockito.eq(recordingName));
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

//     @Test
//     void shouldCreateSnapshot() throws Exception {
//         IRecordingDescriptor recordingDescriptor = Mockito.mock(IRecordingDescriptor.class);
//         Mockito.when(recordingDescriptor.getName()).thenReturn("THESNAPSHOT");
//         Mockito.when(recordingDescriptor.getId()).thenReturn(1234L);

//         IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
//         JFRConnection conn = Mockito.mock(JFRConnection.class);
//         Mockito.when(conn.getService()).thenReturn(svc);
//         Mockito.when(svc.getSnapshotRecording()).thenReturn(recordingDescriptor);

//         RecordingOptionsBuilder recordingOptionsBuilder =
//                 Mockito.mock(RecordingOptionsBuilder.class);
//         Mockito.when(recordingOptionsBuilderFactory.create(svc))
//                 .thenReturn(recordingOptionsBuilder);
//         IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
//         Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);    
//     }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
        IQuantity zeroQuantity = Mockito.mock(IQuantity.class);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.lenient().when(descriptor.getId()).thenReturn(1L);
        Mockito.lenient().when(descriptor.getName()).thenReturn(name);
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
