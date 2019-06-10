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

import com.google.gson.Gson;
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
    Gson gson;
    @Mock MessagingServer server;
    @Mock Session session;
    @Mock RemoteEndpoint remote;

    @BeforeEach
    void setup() {
        gson = new GsonBuilder().serializeNulls().create();
        crw = new WsClientReaderWriter(server, gson);
    }

    @Test
    void shouldSetServerConnection() {
        verify(server).addConnection(crw);
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
    void testHasMessage() {
        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(false));
        crw.onWebSocketText("hello world");
        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(true));
        crw.readLine();
        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(false));
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

            ResponseMessage<String> message = new SuccessResponseMessage<>("foo", expectedText);
            long start = System.nanoTime();
            crw.flush(message);
            long delta = System.nanoTime() - start;
            verify(remote).sendString(gson.toJson(message));
            verify(remote).flush();
            MatcherAssert.assertThat(delta, Matchers.allOf(
                Matchers.greaterThan((long) (expectedDelta * 0.75)),
                Matchers.lessThan((long) (expectedDelta * 1.25))
            ));
        });
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
            crw.flush(new SuccessResponseMessage<>("foo", "hello world"));
            verifyZeroInteractions(remote);
            MatcherAssert.assertThat(errStream.toString(), Matchers.equalTo("java.io.IOException\n"));
        } finally {
            System.setErr(origErr);
        }
    }

}