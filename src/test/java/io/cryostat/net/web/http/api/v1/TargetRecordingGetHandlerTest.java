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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingTargetHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpHeaders;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingGetHandlerTest {

    TargetRecordingGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock Optional<InputStream> stream;

    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock CryostatFlightRecorderService service;

    @BeforeEach
    void setup() {
        when(httpServer.getVertx()).thenReturn(vertx);
        this.handler =
                new TargetRecordingGetHandler(
                        authManager,
                        credentialsManager,
                        targetConnectionManager,
                        httpServer,
                        recordingTargetHelper,
                        logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
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
                        Set.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldNotBeAsync() {
        Assertions.assertFalse(handler.isAsync());
    }

    @Test
    void shouldRespond404IfRecordingNameNotFound() throws Exception {
        String recordingName = "someRecording";

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(resp);
        when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        when(ctx.pathParam("targetId")).thenReturn("fooHost:0");
        when(ctx.pathParam("recordingName")).thenReturn(recordingName);

        CompletableFuture<Optional<InputStream>> future = mock(CompletableFuture.class);
        when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.eq(recordingName)))
                .thenReturn(future);
        when(future.get()).thenReturn(stream);
        when(stream.isEmpty()).thenReturn(true);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldRespond500IfUnexpectedExceptionThrown() throws Exception {
        String recordingName = "someRecording";

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(resp);
        when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        when(ctx.pathParam("targetId")).thenReturn("fooHost:0");
        when(ctx.pathParam("recordingName")).thenReturn(recordingName);

        CompletableFuture<Optional<InputStream>> future = mock(CompletableFuture.class);
        when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.eq(recordingName)))
                .thenReturn(future);
        when(future.get())
                .thenThrow(
                        new ExecutionException(
                                "fake exception for testing purposes", new NullPointerException()));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldRespond500IfIOExceptionThrownDuringRecordingDownloadRequest() throws Exception {
        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse resp = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(resp);
        when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        when(ctx.pathParam("targetId")).thenReturn("fooHost:0");
        when(ctx.pathParam("recordingName")).thenReturn("someRecording");

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        CompletableFuture<Optional<InputStream>> future = mock(CompletableFuture.class);
        when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.eq("someRecording")))
                .thenReturn(future);
        ByteArrayInputStream source = new ByteArrayInputStream(src);
        when(future.get()).thenReturn(Optional.of(source));

        // **************Mocking specific to OutputToReadStream****************
        Context context = mock(Context.class);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(
                        invocation -> {
                            Handler<Void> action = invocation.getArgument(0);
                            action.handle(null);
                            return null;
                        })
                .when(context)
                .runOnContext(Mockito.any(Handler.class));

        when(targetConnectionManager.markConnectionInUse(Mockito.any())).thenReturn(false);
        // ********************************************************************

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(
                ex.getCause().getCause().getMessage(),
                Matchers.equalTo(
                        "Target connection unexpectedly closed while streaming recording"));
    }

    @Test
    void shouldHandleRecordingDownloadRequest() throws Exception {
        shouldHandleRecordingDownloadRequest("someRecording");
    }

    @Test
    void shouldHandleRecordingDownloadRequestWithJfrSuffix() throws Exception {
        shouldHandleRecordingDownloadRequest("someRecording.jfr");
    }

    private void shouldHandleRecordingDownloadRequest(String recordingName) throws Exception {
        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse resp = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(resp);
        when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        when(ctx.pathParam("targetId")).thenReturn("fooHost:0");
        when(ctx.pathParam("recordingName")).thenReturn(recordingName);

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        CompletableFuture<Optional<InputStream>> future = mock(CompletableFuture.class);
        when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.eq("someRecording")))
                .thenReturn(future);
        ByteArrayInputStream source = new ByteArrayInputStream(src);
        when(future.get()).thenReturn(Optional.of(source));

        // **************Mocking specific to OutputToReadStream***************
        Buffer dst = Buffer.buffer(1024 * 1024);
        doAnswer(
                        invocation -> {
                            BufferImpl chunk = invocation.getArgument(0);
                            dst.appendBuffer(chunk);
                            return resp;
                        })
                .when(resp)
                .write(Mockito.any(BufferImpl.class), Mockito.any(Handler.class));

        Context context = mock(Context.class);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(
                        invocation -> {
                            Handler<Void> action = invocation.getArgument(0);
                            action.handle(null);
                            return null;
                        })
                .when(context)
                .runOnContext(Mockito.any(Handler.class));

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Void>> handler = invocation.getArgument(0);
                            handler.handle(Future.succeededFuture());
                            return null;
                        })
                .when(resp)
                .end(Mockito.any(Handler.class));

        when(targetConnectionManager.markConnectionInUse(Mockito.any())).thenReturn(true);
        // ********************************************************************

        handler.handle(ctx);

        Assertions.assertArrayEquals(src, dst.getBytes());
        verify(resp).setChunked(true);
        verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
        verify(resp).end(Mockito.any(Handler.class));
    }
}
