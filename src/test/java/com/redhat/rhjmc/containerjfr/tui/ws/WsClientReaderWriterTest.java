/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;

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
}
