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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
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

@ExtendWith(MockitoExtension.class)
class TargetRecordingUploadPostHandlerTest {

    TargetRecordingUploadPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock WebClient webClient;
    @Mock FileSystem fs;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;
    @Mock JFRConnection conn;

    static final String DATASOURCE_URL = "http://localhost:8080";

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingUploadPostHandler(
                        auth,
                        credentialsManager,
                        env,
                        targetConnectionManager,
                        30,
                        webClient,
                        fs,
                        logger);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(),
                Matchers.equalTo("/api/v1/targets/:targetId/recordings/:recordingName/upload"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(
                        Set.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING)));
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
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(rawUrl);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(501));
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:1234");
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(conn));
        CryostatFlightRecorderService svc = Mockito.mock(CryostatFlightRecorderService.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class,
                        () -> {
                            handler.handle(ctx);
                        });
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(
                ex.getCause(), Matchers.instanceOf(RecordingNotFoundException.class));
    }

    @Test
    void shouldDoUpload() throws Exception {
        Path tempFile = Mockito.mock(Path.class);
        Mockito.when(fs.createTempFile(null, null)).thenReturn(tempFile);

        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(conn));
        CryostatFlightRecorderService svc = Mockito.mock(CryostatFlightRecorderService.class);
        IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
        Mockito.when(rec.getName()).thenReturn("foo");
        Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

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

        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
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
        Path tempFile = Mockito.mock(Path.class);
        Mockito.when(fs.createTempFile(null, null)).thenReturn(tempFile);

        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(conn));
        CryostatFlightRecorderService svc = Mockito.mock(CryostatFlightRecorderService.class);
        IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
        Mockito.when(rec.getName()).thenReturn("foo");
        Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

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

        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
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
        Path tempFile = Mockito.mock(Path.class);
        Mockito.when(fs.createTempFile(null, null)).thenReturn(tempFile);

        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(conn));
        CryostatFlightRecorderService svc = Mockito.mock(CryostatFlightRecorderService.class);
        IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
        Mockito.when(rec.getName()).thenReturn("foo");
        Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

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

        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
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
                                + " or server may not be functioning properly: 200 null"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }

    @Test
    void shouldHandleNullResponseBody() throws Exception {
        Path tempFile = Mockito.mock(Path.class);
        Mockito.when(fs.createTempFile(null, null)).thenReturn(tempFile);

        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(conn));
        CryostatFlightRecorderService svc = Mockito.mock(CryostatFlightRecorderService.class);
        IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
        Mockito.when(rec.getName()).thenReturn("foo");
        Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
        Mockito.when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooTarget");
        Mockito.when(ctx.pathParam("recordingName")).thenReturn("foo");

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

        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
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
                                + " or server may not be functioning properly: 200 OK"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(webClient).postAbs(urlCaptor.capture());
        MatcherAssert.assertThat(
                urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
    }
}
