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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.platform.ServiceRef;
import io.cryostat.net.security.SecurityContext;

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
    @Mock DiscoveryStorage storage;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock Optional<InputStream> stream;

    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        when(httpServer.getVertx()).thenReturn(vertx);
        this.handler =
                new TargetRecordingGetHandler(
                        authManager,
                        credentialsManager,
                        targetConnectionManager,
                        storage,
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
    void shouldUseSecurityContextForTarget() throws Exception {
        String targetId = "fooHost:0";

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        when(ctx.pathParam("targetId")).thenReturn(targetId);

        ServiceRef sr = mock(ServiceRef.class);
        when(storage.lookupServiceByTargetId(targetId)).thenReturn(Optional.of(sr));
        SecurityContext sc = Mockito.mock(SecurityContext.class);
        when(authManager.contextFor(sr)).thenReturn(sc);

        SecurityContext actual = handler.securityContext(ctx);
        MatcherAssert.assertThat(actual, Matchers.sameInstance(sc));
        verify(storage).lookupServiceByTargetId(targetId);
        verify(authManager).contextFor(sr);
    }

    @Test
    void shouldRespond404IfRecordingNameNotFound() throws Exception {
        String recordingName = "someRecording";

        ServiceRef sr = mock(ServiceRef.class);
        when(storage.lookupServiceByTargetId(anyString())).thenReturn(Optional.of(sr));
        when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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

        ServiceRef sr = mock(ServiceRef.class);
        when(storage.lookupServiceByTargetId(anyString())).thenReturn(Optional.of(sr));
        when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
        ServiceRef sr = mock(ServiceRef.class);
        when(storage.lookupServiceByTargetId(anyString())).thenReturn(Optional.of(sr));
        when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
        ServiceRef sr = mock(ServiceRef.class);
        when(storage.lookupServiceByTargetId(anyString())).thenReturn(Optional.of(sr));
        when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

        when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
