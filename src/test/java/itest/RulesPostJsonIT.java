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
package itest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
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
        testRule.put("contexts", new JsonArray(List.of("__DEFAULT__")));
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
