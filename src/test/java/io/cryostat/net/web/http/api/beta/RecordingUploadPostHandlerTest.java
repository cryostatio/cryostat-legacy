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
class RecordingUploadPostHandlerTest {

    RecordingUploadPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock WebClient webClient;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Gson gson;

    static final String DATASOURCE_URL = "http://localhost:8080";

    static final String sourceTarget = "foo";
    static final String recordingName = "bar";

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingUploadPostHandler(
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
                    Matchers.equalTo("/api/beta/recordings/:sourceTarget/:recordingName/upload"));
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
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            ExecutionException e = Mockito.mock(ExecutionException.class);
            when(future.get()).thenThrow(e);
            when(e.getCause())
                    .thenReturn(new RecordingNotFoundException(sourceTarget, recordingName));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldDoUpload() throws Exception {
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
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
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
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
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
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
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));
            when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(DATASOURCE_URL);

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
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
