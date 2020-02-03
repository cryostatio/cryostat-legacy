package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

import com.google.gson.Gson;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingServerTest {

    MessagingServer server;
    @Mock Environment env;
    @Mock Logger logger;
    @Mock HttpServer httpServer;
    @Mock AuthManager authManager;
    @Mock Gson gson;
    @Mock WsClientReaderWriter crw1;
    @Mock WsClientReaderWriter crw2;
    @Mock ServerWebSocket sws;

    @BeforeEach
    void setup() {
        when(env.getEnv(Mockito.eq(MessagingServer.MAX_CONNECTIONS_ENV_VAR), Mockito.anyString()))
                .thenReturn("2");
        server = new MessagingServer(httpServer, env, authManager, logger, gson);
    }

    @Test
    void startShouldStartHttpServer() throws Exception {
        verifyZeroInteractions(httpServer);
        server.start();
        verify(httpServer, times(1)).start();
        verify(httpServer, times(1)).websocketHandler(any());
        verifyNoMoreInteractions(httpServer);
    }

    @Test
    void repeatConnectionShouldNotClosePrevious() {
        server.addConnection(crw1);

        server.addConnection(crw2);
        verify(crw1, Mockito.never()).close();
    }

    @Test
    void clientReaderShouldPropagateClose() throws IOException {
        server.addConnection(crw1);
        server.getClientReader().close();
        verify(crw1).close();
    }

    @Test
    void clientReaderShouldBlockUntilConnected() {
        String expectedText = "hello world";
        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        assertTimeoutPreemptively(
                Duration.ofNanos(expectedDelta * 3),
                () -> {
                    when(crw2.readLine()).thenReturn(expectedText);
                    Executors.newSingleThreadScheduledExecutor()
                            .schedule(
                                    () -> {
                                        server.addConnection(crw1);
                                        server.addConnection(crw2);
                                    },
                                    expectedDelta,
                                    TimeUnit.NANOSECONDS);

                    long start = System.nanoTime();
                    String res = server.getClientReader().readLine();
                    long delta = System.nanoTime() - start;
                    MatcherAssert.assertThat(res, Matchers.equalTo(expectedText));
                    MatcherAssert.assertThat(
                            delta,
                            Matchers.allOf(
                                    Matchers.greaterThan((long) (expectedDelta * 0.75)),
                                    Matchers.lessThan((long) (expectedDelta * 1.25))));
                });
    }

    @Test
    void webSocketCloseHandlerShouldRemoveConnection()
            throws SocketException, UnknownHostException {
        SocketAddress addr = Mockito.mock(SocketAddress.class);
        when(addr.toString()).thenReturn("mockaddr");
        when(sws.remoteAddress()).thenReturn(addr);
        when(sws.path()).thenReturn("/command");
        server.start();

        ArgumentCaptor<Handler> websocketHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpServer).websocketHandler(websocketHandlerCaptor.capture());
        websocketHandlerCaptor.getValue().handle(sws);

        ArgumentCaptor<Handler> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        InOrder inOrder = Mockito.inOrder(sws);
        inOrder.verify(sws).closeHandler(closeHandlerCaptor.capture());
        inOrder.verify(sws).textMessageHandler(Mockito.any(Handler.class));
        inOrder.verify(sws).accept();
        inOrder.verifyNoMoreInteractions();
        closeHandlerCaptor.getValue().handle(null);
        // TODO verify that the WsClientReaderWriter is closed and removed
    }

    @Test
    void shouldHandleRemovedConnections() {
        String expectedText = "hello world";
        when(crw2.readLine()).thenReturn(expectedText);

        server.addConnection(crw1);
        server.addConnection(crw2);

        MatcherAssert.assertThat(
                server.getClientReader().readLine(), Matchers.equalTo(expectedText));
        verify(crw2).readLine();

        ResponseMessage<String> successResponseMessage =
                new SuccessResponseMessage<>("msgId", "test", "message");
        server.flush(successResponseMessage);

        verify(crw1).flush(successResponseMessage);
        verify(crw2).flush(successResponseMessage);

        server.removeConnection(crw2);
        verify(crw2).close();

        String newText = "another message";
        when(crw1.readLine()).thenReturn(newText);

        MatcherAssert.assertThat(server.getClientReader().readLine(), Matchers.equalTo(newText));
        verify(crw1, Mockito.atLeastOnce()).readLine();
        verifyNoMoreInteractions(crw2);

        ResponseMessage<String> failureResponseMessage =
                new FailureResponseMessage("msgId", "test", "failure");
        server.flush(failureResponseMessage);

        verify(crw1).flush(failureResponseMessage);
        verifyNoMoreInteractions(crw2);
    }

    @Test
    void clientWriterShouldDropMessages() {
        server.getClientWriter().print("foo");
        verify(crw1, Mockito.never()).print(Mockito.anyString());
    }

    @Test
    void clientWriterShouldLogExceptions() {
        server.getClientWriter().println(new NullPointerException("Testing Exception"));
        verify(crw1, Mockito.never()).print(Mockito.anyString());
        ArgumentCaptor<Exception> logCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(logger).warn(logCaptor.capture());
        MatcherAssert.assertThat(logCaptor.getValue(), Matchers.isA(NullPointerException.class));
        MatcherAssert.assertThat(
                logCaptor.getValue().getMessage(), Matchers.equalTo("Testing Exception"));
    }

    @Test
    void serverFlushShouldDelegateToAllClientWriters() {
        server.addConnection(crw1);
        server.addConnection(crw2);
        ResponseMessage<String> message = new SuccessResponseMessage<>("msgId", "test", "message");
        server.flush(message);
        verify(crw1).flush(message);
        verify(crw2).flush(message);
    }
}
