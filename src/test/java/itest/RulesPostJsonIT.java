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
import java.util.concurrent.ExecutionException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RulesPostJsonIT extends StandardSelfTest {

    static JsonObject testRule;

    static final Map<String, String> NULL_RESULT = new HashMap<>();

    static final String TEST_RULE_NAME = "Test_Rule";

    static {
        NULL_RESULT.put("result", null);
    }

    @BeforeAll
    static void setup() throws Exception {
        testRule = new JsonObject();
        testRule.put("name", "Test Rule");
        testRule.put("matchExpression", "target.alias == 'es.andrewazor.demo.Main'");
        testRule.put("description", "AutoRulesIT automated rule");
        testRule.put("eventSpecifier", "template=Continuous,type=TARGET");
    }

    @Test
    void testAddRuleThrowsWhenJsonAttributesNull() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                .sendJsonObject(
                        null,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    void testAddRuleThrowsWhenMimeUnsupported() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "NOTAMIME;text/plain")
                .sendJsonObject(
                        testRule,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(415));
        MatcherAssert.assertThat(
                ex.getCause().getMessage(), Matchers.equalTo("Unsupported Media Type"));
    }

    @Test
    void testAddRuleThrowsWhenMimeInvalid() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "NOTAMIME")
                .sendJsonObject(
                        testRule,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(415));
        MatcherAssert.assertThat(
                ex.getCause().getMessage(), Matchers.equalTo("Unsupported Media Type"));
    }

    @Test
    void testAddRuleThrowsWhenRuleNameAlreadyExists() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        try {
            webClient
                    .post("/api/v2/rules")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .sendJsonObject(
                            testRule,
                            ar -> {
                                if (assertRequestStatus(ar, response)) {
                                    response.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject expectedresponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                            Map.of(
                                                    "type",
                                                    HttpMimeType.JSON.mime(),
                                                    "status",
                                                    "Created"),
                                    "data", Map.of("result", TEST_RULE_NAME)));
            MatcherAssert.assertThat(response.get(), Matchers.equalTo(expectedresponse));

            CompletableFuture<JsonObject> duplicatePostResponse = new CompletableFuture<>();
            webClient
                    .post("/api/v2/rules")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .sendJsonObject(
                            testRule,
                            ar -> {
                                assertRequestStatus(ar, duplicatePostResponse);
                            });

            ExecutionException ex =
                    Assertions.assertThrows(
                            ExecutionException.class, () -> duplicatePostResponse.get());
            MatcherAssert.assertThat(
                    ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(409));
            MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Conflict"));

        } finally {
            // clean up rule before running next test
            CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("/api/v2/rules/%s", TEST_RULE_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteResponse)) {
                                    deleteResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject expectedDeleteResponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                    Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                    "data",
                                    NULL_RESULT));
            try {
                MatcherAssert.assertThat(
                        deleteResponse.get(), Matchers.equalTo(expectedDeleteResponse));
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete rule %s", TEST_RULE_NAME), e);
            }
        }
    }

    @Test
    void testAddRuleThrowsWhenIntegerAttributesNegative() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        testRule.put("archivalPeriodSeconds", -60);
        testRule.put("preservedArchives", -3);

        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                .sendJsonObject(
                        testRule,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });

        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }
}
