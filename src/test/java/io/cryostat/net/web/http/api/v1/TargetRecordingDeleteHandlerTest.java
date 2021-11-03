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
package io.cryostat.net.web.http.api.v1;

import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingTargetHelper;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetRecordingDeleteHandlerTest {

    TargetRecordingDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock ReportService reportService;
    @Mock RecordingTargetHelper recordingTargetHelper;

    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingDeleteHandler(
                        auth, recordingTargetHelper);
    }

    @Test
    void shouldHandleDELETE() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(),
                Matchers.equalTo("/api/v1/targets/:targetId/recordings/:recordingName"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(
                        Set.of(ResourceAction.READ_TARGET, ResourceAction.DELETE_RECORDING)));
    }

    @Test
    void shouldDeleteRecording() throws Exception {
        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
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
        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        handler.handleAuthenticated(ctx);

        Mockito.verify(service).close(descriptor);
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        Mockito.verify(reportService)
                .delete(
                        Mockito.argThat(
                                arg ->
                                        arg.getTargetId()
                                                .equals(connectionDescriptor.getTargetId())),
                        Mockito.eq("someRecording"));
        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        inOrder.verify(resp).end();

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("RecordingDeleted");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(Map.of("recording", "someRecording", "target", "fooTarget"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
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

        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

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
