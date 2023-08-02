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
package io.cryostat.net.web.http.generic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import io.cryostat.ApplicationVersion;
import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.web.http.HttpMimeType;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class HealthGetHandlerTest {

    HealthGetHandler handler;
    @Mock ApplicationVersion appVersion;
    @Mock WebClient webClient;
    @Mock Environment env;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new HealthGetHandler(appVersion, webClient, env, gson, logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/health"));
    }

    @Test
    void shouldNotBeAsync() {
        Assertions.assertFalse(handler.isAsync());
    }

    @Test
    void shouldHandleHealthRequest() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", false,
                                "dashboardAvailable", false,
                                "datasourceConfigured", false,
                                "datasourceAvailable", false,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }

    @Test
    void shouldHandleHealthRequestWithDatasourceUrl() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        String url = "http://hostname:1/";
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DATASOURCE_URL")).thenReturn(url);
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(false);

        HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.get(Mockito.anyString(), Mockito.anyString())).thenReturn(req);
        Mockito.when(req.port(Mockito.anyInt())).thenReturn(req);
        Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
        Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(resp);
                                Mockito.when(resp.statusCode()).thenReturn(200);
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(0))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(req)
                .send(Mockito.any());

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", false,
                                "dashboardAvailable", false,
                                "datasourceConfigured", true,
                                "datasourceAvailable", true,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }

    @Test
    void shouldHandleHealthRequestWithDashboardUrl() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        String url = "http://hostname:1/";
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL")).thenReturn(url);
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(false);

        HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.get(Mockito.anyString(), Mockito.anyString())).thenReturn(req);
        Mockito.when(req.port(Mockito.anyInt())).thenReturn(req);
        Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
        Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(resp);
                                Mockito.when(resp.statusCode()).thenReturn(200);
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(0))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(req)
                .send(Mockito.any());

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", true,
                                "dashboardAvailable", true,
                                "datasourceConfigured", false,
                                "datasourceAvailable", false,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }

    @Test
    void shouldHandleHealthRequestWithConfiguredButUnhealthyService() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        String url = "http://hostname:1/";
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL")).thenReturn(url);
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(false);

        HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.get(Mockito.anyString(), Mockito.anyString())).thenReturn(req);
        Mockito.when(req.port(Mockito.anyInt())).thenReturn(req);
        Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
        Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(resp);
                                Mockito.when(resp.statusCode()).thenReturn(500);
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(0))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(req)
                .send(Mockito.any());

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", true,
                                "dashboardAvailable", false,
                                "datasourceConfigured", false,
                                "datasourceAvailable", false,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }

    @Test
    void shouldHandleHealthRequestWithConfiguredButUnreachableService() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        String url = "http://hostname:1/";
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL")).thenReturn(url);
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(false);

        HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
        Mockito.when(webClient.get(Mockito.anyString(), Mockito.anyString())).thenReturn(req);
        Mockito.when(req.port(Mockito.anyInt())).thenReturn(req);
        Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
        Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.failed()).thenReturn(true);
                                Mockito.when(asyncResult.cause())
                                        .thenReturn(new Exception("test failure: unreachable"));
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(0))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(req)
                .send(Mockito.any());

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", true,
                                "dashboardAvailable", false,
                                "datasourceConfigured", false,
                                "datasourceAvailable", false,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }

    @Test
    void shouldHandleHealthRequestWithDashboardUrlWithoutExplicitPort() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(appVersion.getVersionString()).thenReturn("v1.2.3");

        String url = "https://hostname/";
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL")).thenReturn(url);
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(false);

        HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(webClient.get(Mockito.anyString(), Mockito.anyString())).thenReturn(req);
        Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
        Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                AsyncResult<HttpResponse<Buffer>> asyncResult =
                                        Mockito.mock(AsyncResult.class);
                                Mockito.when(asyncResult.result()).thenReturn(resp);
                                Mockito.when(resp.statusCode()).thenReturn(200);
                                ((Handler<AsyncResult<HttpResponse<Buffer>>>) args.getArgument(0))
                                        .handle(asyncResult);
                                return null;
                            }
                        })
                .when(req)
                .send(Mockito.any());

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(rep).end(responseCaptor.capture());

        Map<String, Object> responseMap =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());
        MatcherAssert.assertThat(
                responseMap,
                Matchers.equalTo(
                        Map.of(
                                "cryostatVersion", "v1.2.3",
                                "dashboardConfigured", true,
                                "dashboardAvailable", true,
                                "datasourceConfigured", false,
                                "datasourceAvailable", false,
                                "reportsConfigured", false,
                                "reportsAvailable", true)));
    }
}
