package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import com.google.gson.Gson;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WsClientReaderWriterTest extends TestBase {

    WsClientReaderWriter crw;
    Gson gson = MainModule.provideGson();
    @Mock Logger logger;
    @Mock ServerWebSocket sws;

    @BeforeEach
    void setup() {
        crw = new WsClientReaderWriter(logger, gson, sws);
    }

    @Test
    void readLineShouldBlockUntilClosed() {
        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        assertTimeoutPreemptively(
                Duration.ofNanos(expectedDelta * 3),
                () -> {
                    Executors.newSingleThreadScheduledExecutor()
                            .schedule(crw::close, expectedDelta, TimeUnit.NANOSECONDS);

                    long start = System.nanoTime();
                    String res = crw.readLine();
                    long delta = System.nanoTime() - start;
                    MatcherAssert.assertThat(res, Matchers.nullValue());
                    MatcherAssert.assertThat(
                            delta,
                            Matchers.allOf(
                                    Matchers.greaterThan((long) (expectedDelta * 0.75)),
                                    Matchers.lessThan((long) (expectedDelta * 1.25))));
                });
    }

    @Test
    void readLineShouldBlockUntilTextReceived() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));

        long expectedDelta = 500L;
        assertTimeoutPreemptively(
                Duration.ofMillis(expectedDelta * 3),
                () -> {
                    String expected = "hello world";
                    Executors.newSingleThreadScheduledExecutor()
                            .schedule(
                                    () -> crw.handle(expected),
                                    expectedDelta,
                                    TimeUnit.MILLISECONDS);

                    MatcherAssert.assertThat(crw.readLine(), Matchers.equalTo(expected));
                });
    }

    @Test
    void testHasMessage() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));

        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(false));
        crw.handle("hello world");
        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(true));
        crw.readLine();
        MatcherAssert.assertThat(crw.hasMessage(), Matchers.is(false));
    }
}
