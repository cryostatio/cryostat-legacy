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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HealthIT extends StandardSelfTest {

    HttpRequest<Buffer> req;

    @BeforeEach
    void createRequest() {
        req = webClient.get("/health");
    }

    @Test
    void shouldIncludeApplicationVersion() throws Exception {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    future.complete(ar.result().bodyAsJsonObject());
                });
        JsonObject response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Assertions.assertTrue(response.containsKey("cryostatVersion"));
        MatcherAssert.assertThat(
                response.getString("cryostatVersion"), Matchers.not(Matchers.emptyOrNullString()));
        MatcherAssert.assertThat(
                response.getString("cryostatVersion"), Matchers.not(Matchers.equalTo("unknown")));
    }
}
