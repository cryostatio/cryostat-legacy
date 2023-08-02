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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationsUrlGetHandlerTest {

    @Nested
    class WithoutSsl {

        NotificationsUrlGetHandler handler;
        @Mock HttpServer httpServer;
        @Mock NetworkConfiguration netConf;
        @Mock Logger logger;
        Gson gson = MainModule.provideGson(logger);

        @BeforeEach
        void setup() {
            this.handler = new NotificationsUrlGetHandler(gson, httpServer, netConf);
        }

        @Test
        void shouldBeGETRequest() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldHaveCorrectPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v1/notifications_url"));
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
        void shouldHandleNotificationsUrlRequest() throws SocketException, UnknownHostException {
            RoutingContext ctx = mock(RoutingContext.class);
            HttpServerResponse rep = mock(HttpServerResponse.class);
            when(ctx.response()).thenReturn(rep);
            when(netConf.getWebServerHost()).thenReturn("hostname");
            when(netConf.getExternalWebServerPort()).thenReturn(1);

            handler.handle(ctx);

            InOrder inOrder = inOrder(rep);
            inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            inOrder.verify(rep)
                    .end("{\"notificationsUrl\":\"ws://hostname:1/api/v1/notifications\"}");
        }

        @Test
        void shouldBubbleInternalServerErrorIfHandlerThrows()
                throws SocketException, UnknownHostException {
            RoutingContext ctx = mock(RoutingContext.class);
            HttpServerResponse rep = mock(HttpServerResponse.class);
            when(ctx.response()).thenReturn(rep);
            when(netConf.getWebServerHost()).thenThrow(UnknownHostException.class);
            Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        }
    }

    @Nested
    class WithSsl {

        NotificationsUrlGetHandler handler;
        @Mock Logger logger;
        @Mock HttpServer httpServer;
        @Mock NetworkConfiguration netConf;
        Gson gson = MainModule.provideGson(logger);

        @BeforeEach
        void setup() {
            when(httpServer.isSsl()).thenReturn(true);
            this.handler = new NotificationsUrlGetHandler(gson, httpServer, netConf);
        }

        @Test
        void shouldHandleNotificationsUrlRequestWithWss()
                throws SocketException, UnknownHostException {
            RoutingContext ctx = mock(RoutingContext.class);
            HttpServerResponse rep = mock(HttpServerResponse.class);
            when(ctx.response()).thenReturn(rep);
            when(netConf.getWebServerHost()).thenReturn("hostname");
            when(netConf.getExternalWebServerPort()).thenReturn(1);

            handler.handle(ctx);

            InOrder inOrder = inOrder(rep);
            inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            inOrder.verify(rep)
                    .end("{\"notificationsUrl\":\"wss://hostname:1/api/v1/notifications\"}");
        }
    }
}
