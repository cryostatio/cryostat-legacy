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

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.SslConfiguration;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.impl.RoutingContextInternal;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorsEnablingHandlerTest {

    final String CUSTOM_ORIGIN = "http://localhost:9001";
    CorsEnablingHandler handler;
    @Mock Environment env;
    @Mock NetworkConfiguration netConf;
    @Mock SslConfiguration sslConf;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        Mockito.when(env.getEnv("CRYOSTAT_CORS_ORIGIN", CorsEnablingHandler.DEV_ORIGIN))
                .thenReturn(CUSTOM_ORIGIN);
        this.handler = new CorsEnablingHandler(env, netConf, sslConf, logger);
    }

    @Test
    void shouldApplyToAllPaths() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo(RequestHandler.ALL_PATHS));
    }

    @Test
    void shouldApplyToNoSpecificMethod() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.nullValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void availabilityShouldDependOnEnvVar(boolean available) {
        Mockito.when(env.hasEnv("CRYOSTAT_CORS_ORIGIN")).thenReturn(available);
        MatcherAssert.assertThat(handler.isAvailable(), Matchers.equalTo(available));
    }

    @Nested
    class HandlingTest {

        @Mock RoutingContextInternal ctx;
        @Mock HttpServerRequest req;
        @Mock HttpServerResponse res;
        MultiMap headers;

        @BeforeEach
        void setRequestAndResponse() {
            headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.lenient().when(ctx.request()).thenReturn(req);
            Mockito.lenient().when(ctx.response()).thenReturn(res);
            Mockito.lenient().when(req.headers()).thenReturn(headers);
            Mockito.lenient().when(res.headers()).thenReturn(headers);
            Mockito.lenient().when(res.setStatusCode(Mockito.anyInt())).thenReturn(res);
            Mockito.lenient()
                    .when(
                            res.putHeader(
                                    Mockito.any(CharSequence.class),
                                    Mockito.any(CharSequence.class)))
                    .thenReturn(res);
            Mockito.lenient()
                    .when(res.putHeader(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(res);
        }

        @Test
        void shouldPassToNextHandlerOnNonCORS() {
            handler.handle(ctx);

            InOrder inOrder = Mockito.inOrder(ctx, req, res);
            inOrder.verify(ctx).request();
            inOrder.verify(ctx).response();
            inOrder.verify(ctx).request();
            inOrder.verify(req).headers();
            inOrder.verify(ctx).next();
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        void shouldAddHeadersToCORS() {
            headers.set(HttpHeaders.ORIGIN, CUSTOM_ORIGIN);

            handler.handle(ctx);

            Mockito.verify(res).headers();
            Mockito.verify(res).putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            Mockito.verify(res).putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CUSTOM_ORIGIN);
            Mockito.verify(res)
                    .putHeader(
                            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            WebServer.AUTH_SCHEME_HEADER
                                    + ","
                                    + AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER);
            Mockito.verifyNoMoreInteractions(res);
            Mockito.verify(ctx).next();
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST", "PATCH", "OPTIONS", "HEAD", "DELETE"})
        void shouldRespondOKToOPTIONSWithAcceptedMethod(String methodName) {
            HttpMethod method = HttpMethod.valueOf(methodName);
            Mockito.when(req.method()).thenReturn(HttpMethod.OPTIONS);
            headers.set(HttpHeaders.ORIGIN, CUSTOM_ORIGIN);
            headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method.name());

            handler.handle(ctx);

            Mockito.verify(res).putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            Mockito.verify(res).putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CUSTOM_ORIGIN);
            Mockito.verify(res)
                    .putHeader(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                            "GET,POST,PATCH,OPTIONS,HEAD,DELETE");
            Mockito.verify(res)
                    .putHeader(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            "Authorization,X-JMX-Authorization,Content-Type");
            Mockito.verify(res).setStatusCode(204);
            Mockito.verify(res).putHeader(HttpHeaders.CONTENT_LENGTH, "0");
            Mockito.verify(res).end();
            Mockito.verifyNoMoreInteractions(res);
        }

        @Test
        void shouldRespond403ForCORSRequestWithInvalidOrigin() {
            headers.set(HttpHeaders.ORIGIN, "http://example.com:1234/");
            handler.handle(ctx);
            Mockito.verify(ctx).fail(Mockito.eq(403), Mockito.any(IllegalStateException.class));
        }
    }
}
