package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import com.redhat.rhjmc.containerjfr.net.HttpServer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingServerTest {

    MessagingServer server;
    @Mock Logger logger;
    @Mock HttpServer httpServer;
    @Mock Gson gson;
    @Mock WsClientReaderWriter crw1;
    @Mock WsClientReaderWriter crw2;

    @BeforeEach
    void setup() {
        server = new MessagingServer(httpServer, logger, gson);
    }

    @Test
    void startShouldStartJetty() throws Exception {
        verifyZeroInteractions(httpServer);
        server.start();
        verify(httpServer, times(1)).start();
        verify(httpServer, times(1)).websocketHandler(any());
        verifyNoMoreInteractions(httpServer);
    }

    @Test
    void repeatConnectionShouldNotClosePrevious() {
        server.addConnection(crw1);
        verifyZeroInteractions(crw1);

        server.addConnection(crw2);
        verify(crw1, Mockito.never()).close();
        verifyZeroInteractions(crw2);
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
        assertTimeoutPreemptively(Duration.ofNanos(expectedDelta * 3), () -> {
            when(crw1.hasMessage()).thenReturn(false);
            when(crw2.readLine()).thenReturn(expectedText);
            when(crw2.hasMessage()).thenReturn(true);
            Executors.newSingleThreadScheduledExecutor().schedule(() -> { server.addConnection(crw1); server.addConnection(crw2); }, expectedDelta, TimeUnit.NANOSECONDS);

            long start = System.nanoTime();
            String res = server.getClientReader().readLine();
            long delta = System.nanoTime() - start;
            MatcherAssert.assertThat(res, Matchers.equalTo(expectedText));
            MatcherAssert.assertThat(delta, Matchers.allOf(
                Matchers.greaterThan((long) (expectedDelta * 0.75)),
                Matchers.lessThan((long) (expectedDelta * 1.25))
            ));
        });
    }

    @Test
    void shouldHandleRemovedConnections() {
        String expectedText = "hello world";
        when(crw1.hasMessage()).thenReturn(false);
        when(crw2.readLine()).thenReturn(expectedText);
        when(crw2.hasMessage()).thenReturn(true);

        server.addConnection(crw1);
        server.addConnection(crw2);

        MatcherAssert.assertThat(server.getClientReader().readLine(), Matchers.equalTo(expectedText));
        verify(crw1).hasMessage();
        verify(crw2).hasMessage();
        verify(crw2).readLine();

        ResponseMessage<String> successResponseMessage = new SuccessResponseMessage<>("test", "message");
        server.flush(successResponseMessage);

        verify(crw1).flush(successResponseMessage);
        verify(crw2).flush(successResponseMessage);

        server.removeConnection(crw2);
        server.removeConnection(null);

        String newText = "another message";
        when(crw1.hasMessage()).thenReturn(true);
        when(crw1.readLine()).thenReturn(newText);

        MatcherAssert.assertThat(server.getClientReader().readLine(), Matchers.equalTo(newText));
        verify(crw1, Mockito.times(2)).hasMessage();
        verify(crw1).readLine();
        verifyNoMoreInteractions(crw2);

        ResponseMessage<String> failureResponseMessage = new FailureResponseMessage("test", "failure");
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
        MatcherAssert.assertThat(logCaptor.getValue().getMessage(), Matchers.equalTo("Testing Exception"));
    }

    @Test
    void serverFlushShouldDelegateToAllClientWriters() {
        server.addConnection(crw1);
        server.addConnection(crw2);
        ResponseMessage<String> message = new SuccessResponseMessage<>("test", "message");
        server.flush(message);
        verify(crw1).flush(message);
        verify(crw2).flush(message);
    }

}
