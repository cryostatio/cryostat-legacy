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
package io.cryostat.messaging;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.cryostat.MainModule;
import io.cryostat.MockVertx;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthenticatedAction;
import io.cryostat.net.HttpServer;
import io.cryostat.net.web.http.HttpMimeType;

import com.google.gson.Gson;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingServerTest {

    Vertx vertx;
    MessagingServer server;
    @Mock Environment env;
    @Mock Logger logger;
    @Mock HttpServer httpServer;
    @Mock AuthManager authManager;
    Gson gson = MainModule.provideGson(logger);
    @Mock ServerWebSocket sws;
    @Mock Clock clock;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock AuthenticatedAction authAction;

    @BeforeEach
    void setup() {
        this.vertx = MockVertx.vertx();

        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);

        lenient().when(authAction.onSuccess(Mockito.any())).thenReturn(authAction);
        lenient().when(authAction.onFailure(Mockito.any())).thenReturn(authAction);
        lenient()
                .when(authManager.doAuthenticated(Mockito.any(), Mockito.any()))
                .thenReturn(authAction);

        SocketAddress addr = Mockito.mock(SocketAddress.class);
        lenient().when(addr.toString()).thenReturn("mockaddr");
        lenient().when(sws.remoteAddress()).thenReturn(addr);
        lenient().when(sws.path()).thenReturn("/api/v1/notifications");
        lenient().when(sws.isClosed()).thenReturn(false);

        server =
                new MessagingServer(
                        vertx,
                        httpServer,
                        env,
                        authManager,
                        notificationFactory,
                        2,
                        clock,
                        logger,
                        gson);
    }

    @Test
    void shouldNotWriteToLimboClients() throws Exception {
        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        verify(sws).accept();

        TestMessage message = new TestMessage("msgId", "test", "message");
        server.writeMessage(message);
        verify(sws, Mockito.never()).writeTextMessage(gson.toJson(message));
    }

    @Test
    void shouldRejectClientOnWrongPath() throws Exception {
        ServerWebSocket sws = Mockito.mock(ServerWebSocket.class);
        when(sws.path()).thenReturn("/api/incorrect");

        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);

        verify(sws, Mockito.never()).accept();
        verify(sws).reject(404);
    }

    @Test
    void shouldRejectClientOnOldPathWithRedirectStatus() throws Exception {
        ServerWebSocket sws = Mockito.mock(ServerWebSocket.class);
        when(sws.path()).thenReturn("/api/v1/command");

        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);

        verify(sws, Mockito.never()).accept();
        verify(sws).reject(410);
    }

    @Test
    void shouldDropTooManyClients() throws Exception {
        ServerWebSocket sws2 = Mockito.mock(ServerWebSocket.class);
        ServerWebSocket sws3 = Mockito.mock(ServerWebSocket.class);
        SocketAddress addr = Mockito.mock(SocketAddress.class);
        when(addr.toString()).thenReturn("mockaddr");
        when(sws2.remoteAddress()).thenReturn(addr);
        when(sws2.path()).thenReturn("/api/v1/notifications");
        when(sws3.remoteAddress()).thenReturn(addr);
        when(sws3.path()).thenReturn("/api/v1/notifications");

        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        websocketHandlerCaptor.getValue().handle(sws2);
        websocketHandlerCaptor.getValue().handle(sws3);

        verify(sws).accept();
        verify(sws2).accept();
        verify(sws3, Mockito.never()).accept();

        verify(sws, Mockito.never()).reject();
        verify(sws2, Mockito.never()).reject();
        verify(sws3).reject();
    }

    @Test
    void writeShouldDelegateToAllClients() throws Exception {
        ServerWebSocket sws2 = Mockito.mock(ServerWebSocket.class);
        SocketAddress addr = Mockito.mock(SocketAddress.class);
        when(addr.toString()).thenReturn("mockaddr");
        when(addr.host()).thenReturn("client");
        when(addr.port()).thenReturn(12345);
        when(sws2.remoteAddress()).thenReturn(addr);
        when(sws2.path()).thenReturn("/api/v1/notifications");
        when(sws2.isClosed()).thenReturn(false);
        when(sws2.uri()).thenReturn("uri2");

        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        websocketHandlerCaptor.getValue().handle(sws2);
        verify(sws).accept();
        verify(sws2).accept();

        ArgumentCaptor<Handler> textMessageHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).textMessageHandler(textMessageHandlerCaptor.capture());
        verify(sws2).textMessageHandler(textMessageHandlerCaptor.capture());
        textMessageHandlerCaptor.getAllValues().get(0).handle("irrelevant");
        textMessageHandlerCaptor.getAllValues().get(1).handle("irrelevant");

        ArgumentCaptor<Runnable> authSuccessCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(authAction, Mockito.times(2)).onSuccess(authSuccessCaptor.capture());
        authSuccessCaptor.getAllValues().forEach(Runnable::run);

        TestMessage message = new TestMessage("msgId", "test", "message");
        server.writeMessage(message);
        verify(sws).writeTextMessage(gson.toJson(message));
        verify(sws2).writeTextMessage(gson.toJson(message));
    }

    @Test
    void shouldStopHandlingAfterFirstMessage() throws Exception {
        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        verify(sws).accept();

        ArgumentCaptor<Handler> textMessageHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).textMessageHandler(textMessageHandlerCaptor.capture());
        textMessageHandlerCaptor.getValue().handle("irrelevant");

        ArgumentCaptor<Runnable> authSuccessCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(authAction).onSuccess(authSuccessCaptor.capture());
        authSuccessCaptor.getValue().run();

        verify(sws).textMessageHandler(null);
    }

    @Test
    void authFailureShouldRejectConnection() throws Exception {
        AuthenticatedAction authAction =
                new AuthenticatedAction() {
                    private Runnable failureHandler;

                    @Override
                    public AuthenticatedAction onSuccess(Runnable runnable) {
                        return this;
                    }

                    @Override
                    public AuthenticatedAction onFailure(Runnable runnable) {
                        this.failureHandler = runnable;
                        return this;
                    }

                    @Override
                    public void execute()
                            throws InterruptedException, ExecutionException, TimeoutException {
                        failureHandler.run();
                    }
                };
        Mockito.when(authManager.doAuthenticated(Mockito.any(), Mockito.any()))
                .thenReturn(authAction);

        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        verify(sws).accept();

        ArgumentCaptor<Handler> textMessageHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).textMessageHandler(textMessageHandlerCaptor.capture());
        textMessageHandlerCaptor.getValue().handle("irrelevant");

        verify(sws).close((short) 1002, "Invalid auth subprotocol");
    }

    @Test
    void shouldPingAcceptedClients() throws SocketException, UnknownHostException {
        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        verify(sws).accept();

        ArgumentCaptor<Handler> textMessageHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).textMessageHandler(textMessageHandlerCaptor.capture());
        textMessageHandlerCaptor.getValue().handle("irrelevant");

        ArgumentCaptor<Runnable> authSuccessCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(authAction).onSuccess(authSuccessCaptor.capture());
        authSuccessCaptor.getValue().run();

        ArgumentCaptor<Handler<Long>> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(vertx, Mockito.atLeastOnce())
                .setPeriodic(Mockito.anyLong(), handlerCaptor.capture());
        Handler<Long> handler = handlerCaptor.getValue();

        Mockito.verify(sws, Mockito.times(0)).writePing(Mockito.any());
        handler.handle(1234L);
        Mockito.verify(sws, Mockito.times(1)).writePing(Mockito.any());
    }

    @Test
    void closeHandlerShouldCloseWebSocket() throws SocketException, UnknownHostException {
        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);
        verify(sws).accept();

        ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).closeHandler(closeHandlerCaptor.capture());

        ArgumentCaptor<Handler> textMessageHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sws).textMessageHandler(textMessageHandlerCaptor.capture());
        textMessageHandlerCaptor.getValue().handle("irrelevant");

        ArgumentCaptor<Runnable> authSuccessCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(authAction).onSuccess(authSuccessCaptor.capture());
        authSuccessCaptor.getValue().run();
        verify(sws).textMessageHandler(null);

        closeHandlerCaptor.getValue().handle(null);
        verify(sws).close();
    }

    static class TestMessage {
        List<String> msgs;

        TestMessage(String... msgs) {
            this.msgs = Arrays.asList(msgs);
        }
    }
}
