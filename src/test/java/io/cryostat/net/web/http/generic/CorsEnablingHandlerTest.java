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
