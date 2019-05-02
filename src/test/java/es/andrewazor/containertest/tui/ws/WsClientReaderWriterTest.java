package es.andrewazor.containertest.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.GsonBuilder;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class WsClientReaderWriterTest extends TestBase {

    WsClientReaderWriter crw;
    @Mock MessagingServer server;
    @Mock Session session;
    @Mock RemoteEndpoint remote;

    @BeforeEach
    void setup() {
        crw = new WsClientReaderWriter(server, new GsonBuilder().serializeNulls().create());
    }

    @Test
    void shouldSetServerConnection() {
        verify(server).setConnection(crw);
    }

    @Test
    void readLineShouldBlockUntilClosed() {
        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        assertTimeoutPreemptively(Duration.ofNanos(expectedDelta * 3), () -> {
            Executors.newSingleThreadScheduledExecutor().schedule(crw::close, expectedDelta, TimeUnit.NANOSECONDS);

            long start = System.nanoTime();
            String res = crw.readLine();
            long delta = System.nanoTime() - start;
            MatcherAssert.assertThat(res, Matchers.nullValue());
            MatcherAssert.assertThat(delta, Matchers.allOf(
                Matchers.greaterThan((long) (expectedDelta * 0.75)),
                Matchers.lessThan((long) (expectedDelta * 1.25))
            ));
        });
    }

    @Test
    void readLineShouldBlockUntilTextReceived() {
        long expectedDelta = 500L;
        assertTimeoutPreemptively(Duration.ofMillis(expectedDelta * 3), () -> {
            String expected = "hello world";
            Executors.newSingleThreadScheduledExecutor().schedule(() -> crw.onWebSocketText(expected), expectedDelta, TimeUnit.MILLISECONDS);

            MatcherAssert.assertThat(crw.readLine(), Matchers.equalTo(expected));
        });
    }

    @Test
    void closeShouldCloseConnection() {
        when(session.isOpen()).thenReturn(true);
        crw.onWebSocketConnect(session);

        crw.close();
        verify(session, Mockito.times(1)).close();

        crw.close();
        verify(session, Mockito.times(1)).close();
    }

    @Test
    void onWebSocketCloseShouldCloseConnection() {
        when(session.isOpen()).thenReturn(true);
        crw.onWebSocketConnect(session);

        crw.onWebSocketClose(0, null);
        verify(session, Mockito.times(1)).close();

        crw.onWebSocketClose(0, null);
        verify(session, Mockito.times(1)).close();
    }

    @Test
    void flushShouldBlockUntilConnected() {
        when(session.getRemote()).thenReturn(remote);

        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        String expectedText = "hello world";
        assertTimeoutPreemptively(Duration.ofNanos(expectedDelta * 3), () -> {
            Executors.newSingleThreadScheduledExecutor().schedule(() -> crw.onWebSocketConnect(session), expectedDelta, TimeUnit.NANOSECONDS);

            ResponseMessage message = new SuccessResponseMessage();
            message.message = expectedText;
            long start = System.nanoTime();
            crw.flush(message);
            long delta = System.nanoTime() - start;
            verify(remote).sendString("{\"status\":0,\"message\":\"" + expectedText + "\"}");
            verify(remote).flush();
            MatcherAssert.assertThat(delta, Matchers.allOf(
                Matchers.greaterThan((long) (expectedDelta * 0.75)),
                Matchers.lessThan((long) (expectedDelta * 1.25))
            ));
        });
    }

    @Test
    void flushShouldWriteOutBufferedPrintMessages() throws IOException {
        crw.print("disconnected message is discarded");
        when(session.getRemote()).thenReturn(remote);
        crw.onWebSocketConnect(session);
        crw.print("hello world");
        ResponseMessage message = new InvalidCommandResponseMessage();
        crw.flush(message);
        verify(remote).sendString("{\"status\":-1,\"message\":\"hello world\"}");
        verify(remote).flush();
    }

    @Test
    void flushShouldTrimBufferedPrintMessages() throws IOException {
        crw.print("disconnected message is discarded");
        when(session.getRemote()).thenReturn(remote);
        crw.onWebSocketConnect(session);
        crw.println("hello world ");
        ResponseMessage message = new InvalidCommandResponseMessage();
        crw.flush(message);
        verify(remote).sendString("{\"status\":-1,\"message\":\"hello world\"}");
        verify(remote).flush();
    }

    @Test
    void flushShouldHandleIOExceptions() throws IOException {
        PrintStream origErr = System.err;
        try {
            OutputStream errStream = new ByteArrayOutputStream();
            PrintStream err = new PrintStream(errStream);
            System.setErr(err);
            when(session.getRemote()).thenReturn(remote);
            doThrow(IOException.class).when(remote).sendString(Mockito.anyString());
            crw.onWebSocketConnect(session);
            crw.print("hello world");
            crw.flush(new SuccessResponseMessage());
            verifyZeroInteractions(remote);
            MatcherAssert.assertThat(errStream.toString(), Matchers.equalTo("java.io.IOException\n"));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void flushShouldIgnoreBufferedMessagesIfPassedExplicitMessage() throws IOException {
        crw.print("disconnected message is discarded");
        when(session.getRemote()).thenReturn(remote);
        crw.onWebSocketConnect(session);
        crw.print("hello world");
        ResponseMessage message = new InvalidCommandResponseMessage();
        message.message = "override";
        crw.flush(message);
        verify(remote).sendString("{\"status\":-1,\"message\":\"override\"}");
        verify(remote).flush();
    }

    @Test
    void flushShouldWriteBufferedMessagesIfPassedExplicitNullMessage() throws IOException {
        crw.print("disconnected message is discarded");
        when(session.getRemote()).thenReturn(remote);
        crw.onWebSocketConnect(session);
        crw.print("hello world");
        ResponseMessage message = new InvalidCommandResponseMessage();
        message.message = null;
        crw.flush(message);
        verify(remote).sendString("{\"status\":-1,\"message\":\"hello world\"}");
        verify(remote).flush();
    }

    @Test
    void flushShouldWriteBufferedMessagesIfPassedExplicitEmptyMessage() throws IOException {
        crw.print("disconnected message is discarded");
        when(session.getRemote()).thenReturn(remote);
        crw.onWebSocketConnect(session);
        crw.print("hello world");
        ResponseMessage message = new InvalidCommandResponseMessage();
        message.message = "";
        crw.flush(message);
        verify(remote).sendString("{\"status\":-1,\"message\":\"hello world\"}");
        verify(remote).flush();
    }

}