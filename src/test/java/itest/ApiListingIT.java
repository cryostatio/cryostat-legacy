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
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApiListingIT extends StandardSelfTest {

    HttpRequest<Buffer> req;

    @BeforeEach
    void createRequest() {
        req = webClient.get("/api");
    }

    @Test
    void shouldIncludeHttpApiMdAndNonEmptyEndpoints() throws Exception {
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

        MatcherAssert.assertThat(response.getJsonObject("meta"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("type"),
                Matchers.equalTo("application/json"));
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("status"), Matchers.equalTo("OK"));

        MatcherAssert.assertThat(response.getJsonObject("data"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("data").getJsonObject("result"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("data").getJsonObject("result").getString("overview"),
                Matchers.equalTo(
                        String.format("http://%s:%d/HTTP_API.md", Utils.WEB_HOST, Utils.WEB_PORT)));
        MatcherAssert.assertThat(
                response.getJsonObject("data").getJsonObject("result").getJsonArray("endpoints"),
                Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("data")
                        .getJsonObject("result")
                        .getJsonArray("endpoints")
                        .size(),
                Matchers.greaterThan(0));
    }
}
