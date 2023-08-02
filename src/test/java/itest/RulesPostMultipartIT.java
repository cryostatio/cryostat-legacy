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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RulesPostMultipartIT extends StandardSelfTest {
    static final String TEST_RULE_NAME = "Test_Rule";

    static final Map<String, String> NULL_RESULT = new HashMap<>();

    static {
        NULL_RESULT.put("result", null);
    }

    @AfterEach
    void cleanup() throws Exception {
        CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/rules/%s", TEST_RULE_NAME))
                .send(
                        ar -> {
                            if (ar.succeeded()) {
                                deleteResponse.complete(ar.result().bodyAsJsonObject());
                            } else {
                                deleteResponse.completeExceptionally(ar.cause());
                            }
                        });
        try {
            deleteResponse.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Deletion failed. Reason: " + e.getMessage());
            throw e;
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "multipart/form-data",
                "multipart/form-data; boundary=------somecharacters",
                "multipart/form-data; unkown characters",
                "multipart/form-data; directive1; directive2",
                "multipart/form-data; directive"
            })
    void shouldAcceptMultipartWithBoundary(String contentType) throws Exception {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("name", TEST_RULE_NAME);
        form.set("matchExpression", "false");
        form.set("eventSpecifier", "template=Continuous");

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), contentType)
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonObject());
                            } else {
                                response.completeExceptionally(
                                        new RuntimeException("Request failed"));
                            }
                        });
        JsonObject result = response.get(5, TimeUnit.SECONDS);
        MatcherAssert.assertThat(result.getJsonObject("meta"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                result.getJsonObject("meta").getString("status"), Matchers.equalTo("Created"));
        MatcherAssert.assertThat(
                result.getJsonObject("data").getString("name"),
                Matchers.equalTo(result.getJsonArray(TEST_RULE_NAME)));
    }
}
