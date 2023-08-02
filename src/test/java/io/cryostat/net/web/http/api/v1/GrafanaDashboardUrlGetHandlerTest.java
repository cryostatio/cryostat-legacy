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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.web.http.HttpMimeType;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
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
class GrafanaDashboardUrlGetHandlerTest {

    GrafanaDashboardUrlGetHandler handler;
    @Mock Logger logger;
    @Mock Environment env;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new GrafanaDashboardUrlGetHandler(env, gson);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v1/grafana_dashboard_url"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(handler.resourceActions(), Matchers.equalTo(Set.of()));
    }

    @Test
    void shouldBeAsync() {
        Assertions.assertTrue(handler.isAsync());
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequestWithExtUrl() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        String extUrl = "http://ext-hostname:1/path?query=value";
        when(env.hasEnv("GRAFANA_DASHBOARD_EXT_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_EXT_URL")).thenReturn(extUrl);

        handler.handle(ctx);

        verify(env, never()).getEnv("GRAFANA_DASHBOARD_URL");
        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        verify(rep).end("{\"grafanaDashboardUrl\":\"" + extUrl + "\"}");
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequestWithoutExtUrl() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        String url = "http://hostname:1/path?query=value";
        when(env.hasEnv("GRAFANA_DASHBOARD_EXT_URL")).thenReturn(false);
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL")).thenReturn(url);

        handler.handle(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        verify(rep).end("{\"grafanaDashboardUrl\":\"" + url + "\"}");
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequestWithoutEnvVar() {
        RoutingContext ctx = mock(RoutingContext.class);

        when(env.hasEnv("GRAFANA_DASHBOARD_EXT_URL")).thenReturn(false);
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(false);

        HttpException e = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("Internal Server Error"));
        MatcherAssert.assertThat(
                e.getPayload(), Matchers.equalTo("Deployment has no Grafana configuration"));
        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(500));
    }
}
