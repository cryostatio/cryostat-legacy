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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
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
class TargetRecordingDeleteHandlerTest {

    TargetRecordingDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingDeleteHandler(
                        auth, credentialsManager, recordingTargetHelper, logger);
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
    void shouldHandleDeletedRecording() throws Exception {
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someTarget");
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(ctx.response()).thenReturn(resp);

        CompletableFuture<Void> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(
                        recordingTargetHelper.deleteRecording(
                                Mockito.any(), Mockito.eq("someRecording")))
                .thenReturn(future);

        handler.handleAuthenticated(ctx);

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        inOrder.verify(resp).end();
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        CompletableFuture<Void> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(
                        recordingTargetHelper.deleteRecording(
                                Mockito.any(), Mockito.eq("someRecording")))
                .thenReturn(future);
        Mockito.when(future.get())
                .thenThrow(
                        new ExecutionException(
                                new RecordingNotFoundException("someTarget", "someRecording")));

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> handler.handleAuthenticated(ctx));

        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }
}
