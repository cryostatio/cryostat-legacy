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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.cryostat.net.web.http.HttpMimeType;

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

        JsonObject expectedDeleteResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));

        try {
            JsonObject deleteResult = deleteResponse.get(5, TimeUnit.SECONDS);
            MatcherAssert.assertThat(deleteResult, Matchers.equalTo(expectedDeleteResponse));
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
        try {
            JsonObject result = response.get(5, TimeUnit.SECONDS);
            System.out.println("Received response: " + result.toString());

            MatcherAssert.assertThat(result.getJsonObject("meta"), Matchers.notNullValue());
            MatcherAssert.assertThat(
                    result.getJsonObject("meta").getString("status"), Matchers.equalTo("Created"));
            MatcherAssert.assertThat(result.getJsonObject("data"), Matchers.notNullValue());
        } catch (Exception e) {
            System.err.println("Request failed: " + e.getMessage());
            throw e;
        }
    }
}
