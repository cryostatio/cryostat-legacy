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
