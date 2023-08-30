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
package itest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NoopAuthIT extends StandardSelfTest {

    HttpRequest<Buffer> req;

    @BeforeEach
    void createRequest() {
        req = webClient.post("/api/v1/auth");
    }

    @Test
    public void shouldSucceed() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusCode());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo(200));
    }

    @Test
    public void shouldReturnOK() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusMessage());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo("OK"));
    }
}
