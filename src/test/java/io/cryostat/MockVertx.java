/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

        Mockito.lenient().doReturn(TIMER_ID).when(vertx).setTimer(Mockito.anyLong(), Mockito.any());

        return vertx;
    }
}
