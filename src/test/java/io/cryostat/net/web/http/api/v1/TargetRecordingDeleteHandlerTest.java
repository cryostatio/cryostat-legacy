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
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someTarget");
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
