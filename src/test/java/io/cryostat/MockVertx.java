/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockVertx {

    public static final long PERIODIC_TIMER_ID = 1234L;
    public static final long TIMER_ID = 5678L;

    public static Vertx vertx() {
        Vertx vertx = Mockito.mock(Vertx.class);

        Mockito.lenient()
                .doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                Promise promise = Promise.promise();

                                Handler<Promise> promiseHandler = invocation.getArgument(0);
                                promiseHandler.handle(promise);

                                Handler resultHandler = invocation.getArgument(2);
                                AsyncResult result =
                                        new AsyncResult() {
                                            @Override
                                            public Object result() {
                                                return promise.future().result();
                                            }

                                            @Override
                                            public Throwable cause() {
                                                return promise.future().cause();
                                            }

                                            @Override
                                            public boolean succeeded() {
                                                return promise.future().succeeded();
                                            }

                                            @Override
                                            public boolean failed() {
                                                return promise.future().failed();
                                            }
                                        };
                                resultHandler.handle(result);

                                return null;
                            }
                        })
                .when(vertx)
                .executeBlocking(Mockito.any(), Mockito.anyBoolean(), Mockito.any());

        Mockito.lenient()
                .doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock invocation) throws Throwable {
                                Promise promise = Promise.promise();

                                Handler<Promise> promiseHandler = invocation.getArgument(0);
                                promiseHandler.handle(promise);

                                Handler resultHandler = invocation.getArgument(1);
                                AsyncResult result =
                                        new AsyncResult() {
                                            @Override
                                            public Object result() {
                                                return promise.future().result();
                                            }

                                            @Override
                                            public Throwable cause() {
                                                return promise.future().cause();
                                            }

                                            @Override
                                            public boolean succeeded() {
                                                return promise.future().succeeded();
                                            }

                                            @Override
                                            public boolean failed() {
                                                return promise.future().failed();
                                            }
                                        };
                                resultHandler.handle(result);

                                return null;
                            }
                        })
                .when(vertx)
                .executeBlocking(Mockito.any(), Mockito.any());

        Mockito.lenient()
                .when(vertx.executeBlocking(Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                Promise promise = Promise.promise();

                                Handler<Promise> promiseHandler = invocation.getArgument(0);
                                promiseHandler.handle(promise);

                                return promise.future();
                            }
                        });

        Mockito.lenient()
                .doAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                Handler action = invocation.getArgument(0);
                                action.handle(null);
                                return null;
                            }
                        })
                .when(vertx)
                .runOnContext(Mockito.any());

        Mockito.lenient()
                .doReturn(PERIODIC_TIMER_ID)
                .when(vertx)
                .setPeriodic(Mockito.anyLong(), Mockito.any());

        return vertx;
    }
}
