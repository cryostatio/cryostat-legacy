package es.andrewazor.containertest.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Server;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingServerTest {

    MessagingServer server;
    @Mock Server jettyServer;
    @Mock WsClientReaderWriter crw1;
    @Mock WsClientReaderWriter crw2;

    @BeforeEach
    void setup() {
        server = new MessagingServer(jettyServer);
    }

    @Test
    void startShouldStartJetty() throws Exception {
        verifyZeroInteractions(jettyServer);
        server.start();
        verify(jettyServer).start();
        verify(jettyServer).dump(System.err);
        verifyNoMoreInteractions(jettyServer);
    }

    @Test
    void repeatConnectionShouldClosePrevious() {
        server.setConnection(crw1);
        verifyZeroInteractions(crw1);

        server.setConnection(crw2);
        verify(crw1).close();
        verifyZeroInteractions(crw2);
    }

    @Test
    void testGetConnection() {
        MatcherAssert.assertThat(server.getConnection(), Matchers.nullValue());
        server.setConnection(crw1);
        MatcherAssert.assertThat(server.getConnection(), Matchers.sameInstance(crw1));
    }

    @Test
    void clientReaderShouldPropagateClose() throws IOException {
        server.setConnection(crw1);
        server.getClientReader().close();
        verify(crw1).close();
    }

    @Test
    void clientReaderShouldBlockUntilConnected() {
        String expectedText = "hello world";
        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        assertTimeoutPreemptively(Duration.ofNanos(expectedDelta * 3), () -> {
            when(crw1.readLine()).thenReturn(expectedText);
            Executors.newSingleThreadScheduledExecutor().schedule(() -> server.setConnection(crw1), expectedDelta, TimeUnit.NANOSECONDS);

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
    void clientWriterShouldDropMessages() {
        server.getClientWriter().print("foo");
        verify(crw1, Mockito.never()).print(Mockito.anyString());
    }

    @Test
    void clientWriterShouldPrintExceptionsToStdErr() {
        PrintStream err = System.err;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));
            server.getClientWriter().println(new NullPointerException("Testing Exception"));
            verify(crw1, Mockito.never()).print(Mockito.anyString());
            MatcherAssert.assertThat(baos.toString(), Matchers.containsString("NullPointerException: Testing Exception"));
        } finally {
            System.setErr(err);
        }
    }

}