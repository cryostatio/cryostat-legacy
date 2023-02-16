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
package io.cryostat.net.web.http.api.beta;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import com.google.gson.Gson;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
class RecordingUploadPostFromPathHandlerTest {

    RecordingUploadPostFromPathHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock WebClient webClient;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Gson gson;

    static final String DATASOURCE_URL = "http://localhost:8080";

    static final String subdirectoryName = "foo";
    static final String recordingName = "bar";

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingUploadPostFromPathHandler(
                        auth, credentialsManager, env, 30, webClient, recordingArchiveHelper, gson);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeV2Handler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHandlePOST() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
        }

        @Test
        void shouldHandleCorrectPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo(
                            "/api/beta/fs/recordings/:subdirectoryName/:recordingName/upload"));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldNotBeAsyncHandler() {
            Assertions.assertFalse(handler.isAsync());
        }
    }

    @Nested
    class Behaviour {
        @Mock RequestParameters params;

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
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(501));
        }

        @Test
        void shouldThrowExceptionIfRecordingNotFound() throws Exception {
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPathFromPath(
                            Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            ExecutionException e = Mockito.mock(ExecutionException.class);
            when(future.get()).thenThrow(e);
            when(e.getCause())
                    .thenReturn(new RecordingNotFoundException(subdirectoryName, recordingName));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldDoUpload() throws Exception {
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPathFromPath(
                            Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            Path recordingPath = Mockito.mock(Path.class);
            when(future.get()).thenReturn(recordingPath);
            when(recordingPath.toString()).thenReturn("/recordings/foo");

            HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
            HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
            when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
            when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(httpReq);
            when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
            Mockito.doAnswer(
                            new Answer<Void>() {
                                @Override
                                public Void answer(InvocationOnMock args) throws Throwable {
                                    AsyncResult<HttpResponse<Buffer>> asyncResult =
                                            Mockito.mock(AsyncResult.class);
                                    when(asyncResult.result()).thenReturn(httpResp);
                                    when(httpResp.statusCode()).thenReturn(200);
                                    when(httpResp.statusMessage()).thenReturn("OK");
                                    when(httpResp.bodyAsString()).thenReturn("HELLO");
                                    ((Handler<AsyncResult<HttpResponse<Buffer>>>)
                                                    args.getArgument(1))
                                            .handle(asyncResult);
                                    return null;
                                }
                            })
                    .when(httpReq)
                    .sendMultipartForm(Mockito.any(), Mockito.any());

            IntermediateResponse<String> response = handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo("HELLO"));

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(webClient).postAbs(urlCaptor.capture());
            MatcherAssert.assertThat(
                    urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
        }

        @Test
        void shouldHandleInvalidResponseStatusCode() throws Exception {
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPathFromPath(
                            Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            Path recordingPath = Mockito.mock(Path.class);
            when(future.get()).thenReturn(recordingPath);
            when(recordingPath.toString()).thenReturn("/recordings/foo");

            HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
            HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
            when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
            when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
            when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(httpReq);
            Mockito.doAnswer(
                            new Answer<Void>() {
                                @Override
                                public Void answer(InvocationOnMock args) throws Throwable {
                                    AsyncResult<HttpResponse<Buffer>> asyncResult =
                                            Mockito.mock(AsyncResult.class);
                                    when(asyncResult.result()).thenReturn(httpResp);
                                    when(httpResp.statusCode()).thenReturn(418);
                                    when(httpResp.statusMessage()).thenReturn("I'm a teapot");
                                    when(httpResp.bodyAsString()).thenReturn("short and stout");
                                    ((Handler<AsyncResult<HttpResponse<Buffer>>>)
                                                    args.getArgument(1))
                                            .handle(asyncResult);
                                    return null;
                                }
                            })
                    .when(httpReq)
                    .sendMultipartForm(Mockito.any(), Mockito.any());

            ApiException e =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));

            MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
            MatcherAssert.assertThat(
                    e.getMessage(),
                    Matchers.equalTo(
                            "Invalid response from datasource server; datasource URL may be"
                                + " incorrect, or server may not be functioning properly: 418 I'm a"
                                + " teapot"));

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(webClient).postAbs(urlCaptor.capture());
            MatcherAssert.assertThat(
                    urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
        }

        @Test
        void shouldHandleNullStatusMessage() throws Exception {
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPathFromPath(
                            Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            Path recordingPath = Mockito.mock(Path.class);
            when(future.get()).thenReturn(recordingPath);
            when(recordingPath.toString()).thenReturn("/recordings/foo");

            HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
            HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
            when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
            when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(httpReq);
            when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
            Mockito.doAnswer(
                            new Answer<Void>() {
                                @Override
                                public Void answer(InvocationOnMock args) throws Throwable {
                                    AsyncResult<HttpResponse<Buffer>> asyncResult =
                                            Mockito.mock(AsyncResult.class);
                                    when(asyncResult.result()).thenReturn(httpResp);
                                    when(httpResp.statusCode()).thenReturn(200);
                                    when(httpResp.statusMessage()).thenReturn(null);
                                    when(httpResp.bodyAsString()).thenReturn("body");
                                    ((Handler<AsyncResult<HttpResponse<Buffer>>>)
                                                    args.getArgument(1))
                                            .handle(asyncResult);
                                    return null;
                                }
                            })
                    .when(httpReq)
                    .sendMultipartForm(Mockito.any(), Mockito.any());

            ApiException e =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));

            MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
            MatcherAssert.assertThat(
                    e.getMessage(),
                    Matchers.equalTo(
                            "Invalid response from datasource server; datasource URL may be"
                                    + " incorrect, or server may not be functioning properly: 200"
                                    + " null"));

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(webClient).postAbs(urlCaptor.capture());
            MatcherAssert.assertThat(
                    urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
        }

        @Test
        void shouldHandleNullResponseBody() throws Exception {
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPathFromPath(
                            Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            Path recordingPath = Mockito.mock(Path.class);
            when(future.get()).thenReturn(recordingPath);
            when(recordingPath.toString()).thenReturn("/recordings/foo");

            HttpRequest<Buffer> httpReq = Mockito.mock(HttpRequest.class);
            HttpResponse<Buffer> httpResp = Mockito.mock(HttpResponse.class);
            when(webClient.postAbs(Mockito.anyString())).thenReturn(httpReq);
            when(httpReq.addQueryParam(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(httpReq);
            when(httpReq.timeout(Mockito.anyLong())).thenReturn(httpReq);
            Mockito.doAnswer(
                            new Answer<Void>() {
                                @Override
                                public Void answer(InvocationOnMock args) throws Throwable {
                                    AsyncResult<HttpResponse<Buffer>> asyncResult =
                                            Mockito.mock(AsyncResult.class);
                                    when(asyncResult.result()).thenReturn(httpResp);
                                    when(httpResp.statusCode()).thenReturn(200);
                                    when(httpResp.statusMessage()).thenReturn("OK");
                                    when(httpResp.bodyAsString()).thenReturn(null);
                                    ((Handler<AsyncResult<HttpResponse<Buffer>>>)
                                                    args.getArgument(1))
                                            .handle(asyncResult);
                                    return null;
                                }
                            })
                    .when(httpReq)
                    .sendMultipartForm(Mockito.any(), Mockito.any());

            ApiException e =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));

            MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(512));
            MatcherAssert.assertThat(
                    e.getMessage(),
                    Matchers.equalTo(
                            "Invalid response from datasource server; datasource URL may be"
                                + " incorrect, or server may not be functioning properly: 200 OK"));

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(webClient).postAbs(urlCaptor.capture());
            MatcherAssert.assertThat(
                    urlCaptor.getValue(), Matchers.equalTo(DATASOURCE_URL.concat("/load")));
        }
    }
}
