/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.http.generic;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;
import com.redhat.rhjmc.containerjfr.net.web.http.AbstractAuthenticatedRequestHandler;
import com.redhat.rhjmc.containerjfr.net.web.http.RequestHandler;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

@ExtendWith(MockitoExtension.class)
class CorsEnablingHandlerTest {

    final String CUSTOM_ORIGIN = "http://localhost:9001";
    CorsEnablingHandler handler;
    @Mock Environment env;

    @BeforeEach
    void setup() {
        Mockito.when(
                        env.getEnv(
                                CorsEnablingHandler.ENABLE_CORS_ENV,
                                CorsEnablingHandler.DEV_ORIGIN))
                .thenReturn(CUSTOM_ORIGIN);
        this.handler = new CorsEnablingHandler(env);
    }

    @Test
    void shouldApplyToAllPaths() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo(RequestHandler.ALL_PATHS));
    }

    @Test
    void shouldApplyToOtherMethod() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.OTHER));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void availabilityShouldDependOnEnvVar(boolean available) {
        Mockito.when(env.hasEnv(CorsEnablingHandler.ENABLE_CORS_ENV)).thenReturn(available);
        MatcherAssert.assertThat(handler.isAvailable(), Matchers.equalTo(available));
    }

    @Nested
    class HandlingTest {

        @Mock RoutingContext ctx;
        @Mock HttpServerRequest req;
        @Mock HttpServerResponse res;
        @Mock MultiMap headers;

        @BeforeEach
        void setRequestAndResponse() {
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(ctx.response()).thenReturn(res);
            Mockito.when(req.headers()).thenReturn(headers);
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
            Mockito.when(headers.get(HttpHeaders.ORIGIN)).thenReturn(CUSTOM_ORIGIN);

            handler.handle(ctx);

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
        @EnumSource(
                value = HttpMethod.class,
                names = {"GET", "POST", "PATCH", "OPTIONS", "HEAD", "DELETE"})
        void shouldRespondOKToOPTIONSWithAcceptedMethod(HttpMethod method) {
            Mockito.when(req.method()).thenReturn(HttpMethod.OPTIONS);
            Mockito.when(headers.get(HttpHeaders.ORIGIN)).thenReturn(CUSTOM_ORIGIN);
            Mockito.when(headers.get(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD))
                    .thenReturn(method.name());
            Mockito.when(res.setStatusCode(Mockito.anyInt())).thenReturn(res);

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
                            "Authorization,X-JMX-Authorization");
            Mockito.verify(res).setStatusCode(200);
            Mockito.verify(res).end();
            Mockito.verifyNoMoreInteractions(res);
        }

        @Test
        void shouldRespond403ForCORSRequestWithInvalidOrigin() {
            Mockito.when(headers.get(HttpHeaders.ORIGIN)).thenReturn("http://example.com:1234/");
            handler.handle(ctx);
            Mockito.verify(ctx).fail(403);
        }
    }
}
