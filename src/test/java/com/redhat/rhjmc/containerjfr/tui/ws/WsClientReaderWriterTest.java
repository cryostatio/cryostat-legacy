package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class WsClientReaderWriterTest extends TestBase {

    WsClientReaderWriter crw;
    Gson gson;
    @Mock Logger logger;
    @Mock ServerWebSocket sws;

    @BeforeEach
    void setup() {
        gson = new GsonBuilder().serializeNulls().create();
        crw = new WsClientReaderWriter(logger, gson, sws);
    }

    @Test
    void readLineShouldBlockUntilClosed() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));
        doAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    Handler<AsyncResult<Void>> cb = invocation.getArgument(0);
                                    AsyncResult<Void> res = mock(AsyncResult.class);
                                    when(res.failed()).thenReturn(false);
                                    cb.handle(res);
                                    return null;
                                })
                .when(sws)
                .close(any());

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

    @Test
    void closeShouldCloseConnection() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));
        AsyncResult<Void> callbackRes = mock(AsyncResult.class);
        when(callbackRes.failed()).thenReturn(false);
        doAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    Handler<AsyncResult<Void>> cb = invocation.getArgument(0);
                                    cb.handle(callbackRes);
                                    return null;
                                })
                .when(sws)
                .close(any());

        crw.close();
        verify(sws, times(1)).close(any());
    }

    @Test
    void webSocketCloseHandlerShouldCloseConnection() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));

        List<Handler<Void>> closeHandler =
                new ArrayList<>(1); // bypass constraint on being effective final when accessed from
        // anonymous classes
        closeHandler.add(
                0,
                (unused) -> {
                    throw new RuntimeException("Close handler not set!");
                });
        doAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    closeHandler.set(0, invocation.getArgument(0));
                                    return null;
                                })
                .when(sws)
                .closeHandler(any());

        AsyncResult<Void> callbackRes = mock(AsyncResult.class);
        when(callbackRes.failed()).thenReturn(false);
        doAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    Handler<AsyncResult<Void>> cb = invocation.getArgument(0);
                                    cb.handle(callbackRes);
                                    return null;
                                })
                .when(sws)
                .close(any());

        closeHandler.get(0).handle(null);
        verify(sws, times(1)).close(any());
    }

    @Test
    void flushShouldHandleIllegalStateExceptions() {
        when(sws.remoteAddress()).thenReturn(mock(SocketAddress.class));

        doAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    Handler<AsyncResult<Void>> cb = invocation.getArgument(1);
                                    AsyncResult<Void> res = mock(AsyncResult.class);
                                    when(res.failed()).thenReturn(true);
                                    when(res.cause()).thenReturn(new IllegalStateException());
                                    cb.handle(res);
                                    return null;
                                })
                .when(sws)
                .writeTextMessage(any(), any());

        crw.flush(new SuccessResponseMessage<>("foo", "hello world"));
        verify(sws).writeTextMessage(any(), any());
        verify(logger).warn(any(IllegalStateException.class));
    }
}
