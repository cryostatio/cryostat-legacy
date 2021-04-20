/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.messaging;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.web.http.HttpMimeType;

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
    @Mock WsClient wsClient1;
    @Mock WsClient wsClient2;
    @Mock ServerWebSocket sws;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    TestExecutorService executor = new TestExecutorService();

    @BeforeEach
    void setup() {
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

        server =
                new MessagingServer(
                        httpServer,
                        env,
                        authManager,
                        notificationFactory,
                        1,
                        executor,
                        logger,
                        gson);
    }

    @Test
    void repeatConnectionShouldNotClosePrevious() {
        server.addConnection(wsClient1);
        executor.tick();
        server.addConnection(wsClient2);
        executor.tick();

        verify(wsClient1, Mockito.never()).close();
        verify(wsClient2, Mockito.never()).close();
    }

    @Test
    void clientShouldPropagateClose() throws IOException {
        server.addConnection(wsClient1);
        executor.tick();
        server.close();
        executor.tick();
        verify(wsClient1).close();
    }

    @Test
    void clientShouldBlockUntilConnected() {
        String expectedText = "hello world";
        long expectedDelta = TimeUnit.SECONDS.toNanos(1);
        int maxErrorFactor = 10;
        assertTimeoutPreemptively(
                Duration.ofNanos(expectedDelta * maxErrorFactor),
                () -> {
                    when(wsClient2.readMessage()).thenReturn(expectedText);
                    Executors.newSingleThreadScheduledExecutor()
                            .schedule(
                                    () -> {
                                        server.addConnection(wsClient1);
                                        server.addConnection(wsClient2);
                                        executor.tick();
                                    },
                                    expectedDelta,
                                    TimeUnit.NANOSECONDS);

                    long start = System.nanoTime();
                    String res = server.readMessage();
                    long delta = System.nanoTime() - start;
                    MatcherAssert.assertThat(res, Matchers.equalTo(expectedText));
                    MatcherAssert.assertThat(
                            delta,
                            Matchers.allOf(
                                    // actual should never be less than expected, but since this is
                                    // relying on a real wall-clock timer, allow for some error in
                                    // that direction. Allow much more error in the greater-than
                                    // direction to account for system scheduling etc.
                                    Matchers.greaterThan((long) (expectedDelta * 0.9)),
                                    Matchers.lessThan((long) (expectedDelta * maxErrorFactor))));
                });
    }

    @Test
    void webSocketCloseHandlerShouldRemoveConnection()
            throws SocketException, UnknownHostException {
        SocketAddress addr = Mockito.mock(SocketAddress.class);
        when(addr.toString()).thenReturn("mockaddr");
        when(sws.remoteAddress()).thenReturn(addr);
        when(sws.path()).thenReturn("/api/v1/command");
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
        // TODO verify that the WsClient is closed and removed
    }

    @Test
    void shouldHandleRemovedConnections() throws Exception {
        String expectedText = "hello world";
        when(wsClient2.readMessage()).thenReturn(expectedText).thenReturn(null);

        server.addConnection(wsClient1);
        server.addConnection(wsClient2);
        executor.tick();

        MatcherAssert.assertThat(server.readMessage(), Matchers.equalTo(expectedText));
        verify(wsClient2, Mockito.atLeastOnce()).readMessage();

        ResponseMessage<String> successResponseMessage =
                new SuccessResponseMessage<>("msgId", "test", "message");
        server.writeMessage(successResponseMessage);
        executor.tick();

        verify(wsClient1, Mockito.times(1)).writeMessage(gson.toJson(successResponseMessage));
        verify(wsClient2, Mockito.times(1)).writeMessage(gson.toJson(successResponseMessage));

        server.removeConnection(wsClient2);
        executor.tick();
        verify(wsClient2, Mockito.times(1)).close();

        String newText = "another message";
        when(wsClient1.readMessage()).thenReturn(newText).thenReturn(null);
        executor.tick();

        MatcherAssert.assertThat(server.readMessage(), Matchers.equalTo(newText));
        verify(wsClient1, Mockito.atLeastOnce()).readMessage();
        verifyNoMoreInteractions(wsClient2);

        ResponseMessage<String> failureResponseMessage =
                new FailureResponseMessage("msgId", "test", "failure");
        server.writeMessage(failureResponseMessage);
        executor.tick();

        ArgumentCaptor<String> failureCaptor = ArgumentCaptor.forClass(String.class);
        verify(wsClient1, Mockito.times(2)).writeMessage(failureCaptor.capture());
        MatcherAssert.assertThat(
                failureCaptor.getValue(), Matchers.equalTo(gson.toJson(failureResponseMessage)));
        verifyNoMoreInteractions(wsClient2);
    }

    @Test
    void serverWriteShouldDelegateToAllClientWriters() {
        server.addConnection(wsClient1);
        server.addConnection(wsClient2);
        executor.tick();
        ResponseMessage<String> message = new SuccessResponseMessage<>("msgId", "test", "message");
        server.writeMessage(message);
        executor.tick();
        verify(wsClient1).writeMessage(gson.toJson(message));
        verify(wsClient2).writeMessage(gson.toJson(message));
    }

    static class TestExecutorService extends AbstractExecutorService
            implements ScheduledExecutorService {

        private final Map<Runnable, ControlledFuture> futures = new HashMap<>();
        private final List<Runnable> runnables = new ArrayList<>();

        public void tick() {
            runnables.forEach(Runnable::run);
        }

        public ControlledFuture<?> getFuture(Runnable runnable) {
            return futures.get(runnable);
        }

        @Override
        public boolean awaitTermination(long arg0, TimeUnit arg1) throws InterruptedException {
            return false;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public void shutdown() {
            this.runnables.clear();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
            runnables.add(runnable);
            futures.putIfAbsent(runnable, new ControlledFuture<>(unit.toMillis(delay)));
            return futures.get(runnable);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            Runnable runnable =
                    () -> {
                        try {
                            callable.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    };
            runnables.add(runnable);
            futures.putIfAbsent(runnable, new ControlledFuture<>(unit.toMillis(delay)));
            return futures.get(runnable);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable runnable, long delay, long period, TimeUnit unit) {
            runnables.add(runnable);
            futures.putIfAbsent(runnable, new ControlledFuture<>(unit.toMillis(delay)));
            return futures.get(runnable);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable runnable, long delay, long period, TimeUnit unit) {
            runnables.add(runnable);
            futures.putIfAbsent(runnable, new ControlledFuture<>(unit.toMillis(delay)));
            return futures.get(runnable);
        }
    }

    static class ControlledFuture<T> implements ScheduledFuture<T> {

        long delay;
        T v;

        ControlledFuture(long delay) {
            this.delay = delay;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.toMillis(delay);
        }

        @Override
        public int compareTo(Delayed delayed) {
            return 0;
        }

        @Override
        public boolean cancel(boolean force) {
            return false;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return v;
        }

        @Override
        public T get(long period, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return v;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }
    }
}
