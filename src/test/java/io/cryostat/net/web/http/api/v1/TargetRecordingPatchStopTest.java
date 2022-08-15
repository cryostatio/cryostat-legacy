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

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingPatchStopTest {

    TargetRecordingPatchStop patchStop;
    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock RecordingTargetHelper recordingTargetHelper;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.message(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient().when(notificationBuilder.build()).thenReturn(notification);

        this.patchStop = new TargetRecordingPatchStop(recordingTargetHelper);
    }

    @Test
    void shouldThrow404IfNoMatchingRecordingFound() throws Exception {
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        Mockito.when(recordingTargetHelper.stopRecording(Mockito.any(), Mockito.anyString()))
                .thenThrow(new RecordingNotFoundException("myTarget", "someRecording"));

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> patchStop.handle(ctx, connectionDescriptor));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldStopRecording() throws Exception {
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        Mockito.when(ctx.response()).thenReturn(resp);

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("fooTarget");
        patchStop.handle(ctx, connectionDescriptor);

        Mockito.verify(recordingTargetHelper).stopRecording(connectionDescriptor, "someRecording");
        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        inOrder.verify(resp).end();
    }
}
