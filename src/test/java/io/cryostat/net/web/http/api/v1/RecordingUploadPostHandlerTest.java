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

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@Disabled
@ExtendWith(MockitoExtension.class)
class RecordingUploadPostHandlerTest {

    RecordingUploadPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock WebClient webClient;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;

    @Mock RoutingContext ctx;

    static final String DATASOURCE_URL = "http://localhost:8080";

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingUploadPostHandler(
                        auth,
                        credentialsManager,
                        env,
                        30,
                        webClient,
                        recordingArchiveHelper,
                        logger);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/recordings/:recordingName/upload"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "notaurl",
                "almost:/valid",
                "*notok",
                "localhost:",
                "localhost:abc",
                "localhost::9091",
                "http:///localhost:9091",
                "http:://localhost:9091"
            })
    @NullAndEmptySource
    void shouldThrow501IfDatasourceUrlMalformed(String rawUrl) {
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(rawUrl);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(501));
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        String recordingName = "foo";
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(recordingName)).thenReturn(future);
        ExecutionException e = Mockito.mock(ExecutionException.class);
        Mockito.when(future.get()).thenThrow(e);
        Mockito.when(e.getCause())
                .thenReturn(new RecordingNotFoundException("archives", recordingName));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldDoUpload() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(Mockito.anyString()))
                .thenReturn(future);
        Path recordingPath = Mockito.mock(Path.class);
        Mockito.when(future.get()).thenReturn(recordingPath);
        Mockito.when(recordingPath.toString()).thenReturn("/recordings/foo");

        HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
        Mockito.when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(httpReq);
        Mockito.when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(httpResp);
                                Mockito.when(httpResp.statusCode()).thenReturn(200);
                                Mockito.when(httpResp.statusMessage()).thenReturn("OK");
                                Mockito.when(httpResp.bodyAsString()).thenReturn("HELLO");
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(1))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(httpReq)
                .sendMultipartForm(Mockito.any(), Mockito.any());

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(200);
        Mockito.verify(resp).setStatusMessage("OK");
        Mockito.verify(resp).end("HELLO");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }

    @Test
    void shouldHandleInvalidResponseStatusCode() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(Mockito.anyString()))
                .thenReturn(future);
        Path recordingPath = Mockito.mock(Path.class);
        Mockito.when(future.get()).thenReturn(recordingPath);
        Mockito.when(recordingPath.toString()).thenReturn("/recordings/foo");

        HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
        Mockito.when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(httpReq);
        Mockito.when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(httpResp);
                                Mockito.when(httpResp.statusCode()).thenReturn(418);
                                Mockito.when(httpResp.statusMessage()).thenReturn("I'm a teapot");
                                Mockito.when(httpResp.bodyAsString()).thenReturn("short and stout");
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(1))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(httpReq)
                .sendMultipartForm(Mockito.any(), Mockito.any());

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        HttpException e = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));

        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
        MatcherAssert.assertThat(
                e.getPayload(),
                Matchers.equalTo(
                        "Invalid response from datasource server; datasource URL may be incorrect,"
                                + " or server may not be functioning properly: 418 I'm a teapot"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }

    @Test
    void shouldHandleNullStatusMessage() throws Exception {
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(Mockito.anyString()))
                .thenReturn(future);
        Path recordingPath = Mockito.mock(Path.class);
        Mockito.when(future.get()).thenReturn(recordingPath);
        Mockito.when(recordingPath.toString()).thenReturn("/recordings/foo");

        HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
        Mockito.when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(httpReq);
        Mockito.when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(httpResp);
                                Mockito.when(httpResp.statusCode()).thenReturn(200);
                                Mockito.when(httpResp.statusMessage()).thenReturn(null);
                                Mockito.when(httpResp.bodyAsString()).thenReturn("body");
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(1))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(httpReq)
                .sendMultipartForm(Mockito.any(), Mockito.any());

        HttpException e = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));

        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
        MatcherAssert.assertThat(
                e.getPayload(),
                Matchers.equalTo(
                        "Invalid response from datasource server; datasource URL may be incorrect,"
                                + " or server may not be functioning properly: 200 null"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }

    @Test
    void shouldHandleNullResponseBody() throws Exception {
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(Mockito.anyString()))
                .thenReturn(future);
        Path recordingPath = Mockito.mock(Path.class);
        Mockito.when(future.get()).thenReturn(recordingPath);
        Mockito.when(recordingPath.toString()).thenReturn("/recordings/foo");

        HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
        Mockito.when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(httpReq);
        Mockito.when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(httpResp);
                                Mockito.when(httpResp.statusCode()).thenReturn(200);
                                Mockito.when(httpResp.statusMessage()).thenReturn("OK");
                                Mockito.when(httpResp.bodyAsString()).thenReturn(null);
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(1))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(httpReq)
                .sendMultipartForm(Mockito.any(), Mockito.any());

        HttpException e = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));

        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
        MatcherAssert.assertThat(
                e.getPayload(),
                Matchers.equalTo(
                        "Invalid response from datasource server; datasource URL may be incorrect,"
                                + " or server may not be functioning properly: 200 OK"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }
}
