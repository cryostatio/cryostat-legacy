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
