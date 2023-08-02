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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.generic.RequestLoggingHandler.WebServerRequest;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerFormatter;
import io.vertx.ext.web.handler.LoggerHandler;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestLoggingHandlerTest {

    RequestLoggingHandler handler;
    MockedStatic<LoggerHandler> delegateStatic;
    MockedConstruction<WebServerRequest> eventConstruction;
    @Mock LoggerHandler delegate;
    final List eventConstructionArgs = new ArrayList<>();

    @BeforeEach
    void setupEach() {
        delegateStatic = Mockito.mockStatic(LoggerHandler.class);
        delegateStatic
                .when(() -> LoggerHandler.create(Mockito.any(LoggerFormat.class)))
                .thenReturn(delegate);
        Mockito.when(delegate.customFormatter(Mockito.any(LoggerFormatter.class)))
                .thenReturn(delegate);

        eventConstruction =
                Mockito.mockConstruction(
                        WebServerRequest.class,
                        (mock, ctx) -> {
                            eventConstructionArgs.clear();
                            eventConstructionArgs.addAll((List) ctx.arguments());
                        });

        this.handler = new RequestLoggingHandler();
    }

    @AfterEach
    void cleanup() {
        delegateStatic.close();
        eventConstruction.close();
    }

    @Test
    void shouldHandleAnyRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.nullValue());
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("*"));
    }

    @Test
    void shouldHaveGenericVersion() {
        MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.GENERIC));
    }

    @Test
    void shouldBeAsync() {
        Assertions.assertTrue(handler.isAsync());
    }

    @Test
    void shouldHaveZeroPriority() {
        MatcherAssert.assertThat(handler.getPriority(), Matchers.equalTo(0));
    }

    @Test
    void shouldPerformNoResourceActions() {
        MatcherAssert.assertThat(handler.resourceActions(), Matchers.empty());
    }

    @Test
    void shouldHandleRequestByDelegating() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        SocketAddress addr =
                SocketAddress.inetSocketAddress(
                        InetSocketAddress.createUnresolved("localhost", 1234));
        Mockito.when(req.remoteAddress()).thenReturn(addr);
        Mockito.when(req.method()).thenReturn(HttpMethod.GET);
        Mockito.when(req.path()).thenReturn("/some/path");

        HttpServerResponse rep = Mockito.mock(HttpServerResponse.class);
        Mockito.when(req.response()).thenReturn(rep);

        Mockito.verify(delegate, Mockito.never()).handle(Mockito.any());
        MatcherAssert.assertThat(eventConstruction.constructed(), Matchers.empty());

        handler.handle(ctx);

        Mockito.verify(delegate).handle(ctx);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCheckAndCommitJfrEvent(boolean enabled) {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        SocketAddress addr =
                SocketAddress.inetSocketAddress(
                        InetSocketAddress.createUnresolved("localhost", 1234));
        Mockito.when(req.remoteAddress()).thenReturn(addr);
        Mockito.when(req.method()).thenReturn(HttpMethod.GET);
        Mockito.when(req.path()).thenReturn("/some/path");

        HttpServerResponse rep = Mockito.mock(HttpServerResponse.class);
        Mockito.when(req.response()).thenReturn(rep);
        int sc = 200 + ((int) Math.random() * 300);
        Mockito.when(rep.getStatusCode()).thenReturn(sc);

        Mockito.verify(delegate, Mockito.never()).handle(Mockito.any());
        MatcherAssert.assertThat(eventConstruction.constructed(), Matchers.empty());

        handler.handle(ctx);

        MatcherAssert.assertThat(eventConstruction.constructed(), Matchers.hasSize(1));
        WebServerRequest event = eventConstruction.constructed().get(0);

        // We can't make these assertions directly because the event is a mock, so the constructor
        // parameters are not assigned to fields. Instead, store the constructor parameters as a
        // list and compare that to the expected values,
        // MatcherAssert.assertThat(event.host, Matchers.equalTo(addr.host()));
        // MatcherAssert.assertThat(event.port, Matchers.equalTo(addr.port()));
        // MatcherAssert.assertThat(event.method, Matchers.equalTo("GET"));
        // MatcherAssert.assertThat(event.path, Matchers.equalTo("/some/path"));
        MatcherAssert.assertThat(
                eventConstructionArgs,
                Matchers.equalTo(List.of("localhost", 1234, "GET", "/some/path")));

        Mockito.verify(event, Mockito.times(0)).setStatusCode(Mockito.anyInt());
        Mockito.verify(event, Mockito.times(0)).shouldCommit();
        Mockito.verify(event, Mockito.times(0)).commit();

        Mockito.when(event.shouldCommit()).thenReturn(enabled);

        ArgumentCaptor<Handler<Void>> endHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(rep).endHandler(endHandlerCaptor.capture());
        Handler<Void> endHandler = endHandlerCaptor.getValue();
        MatcherAssert.assertThat(endHandler, Matchers.notNullValue());

        endHandler.handle(null);

        Mockito.verify(event, Mockito.times(1)).setStatusCode(sc);
        Mockito.verify(event, Mockito.times(1)).shouldCommit();
        Mockito.verify(event, Mockito.times(enabled ? 1 : 0)).commit();
    }
}
