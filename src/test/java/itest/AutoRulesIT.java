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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.Podman;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class AutoRulesIT extends ExternalTargetsTest {

    static final List<String> CONTAINERS = new ArrayList<>();
    static final Map<String, String> NULL_RESULT = new HashMap<>();

    final String jmxServiceUrl =
            String.format("service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME);
    final String jmxServiceUrlEncoded = jmxServiceUrl.replaceAll("/", "%2F");

    static {
        NULL_RESULT.put("result", null);
    }

    @AfterAll
    static void cleanup() throws Exception {
        for (String id : CONTAINERS) {
            Podman.kill(id);
        }
    }

    @Test
    @Order(1)
    void testAddAndRetrieveRule() throws Exception {
        // assert no rules yet
        CompletableFuture<JsonObject> preRules = new CompletableFuture<>();
        webClient
                .get("/api/v2/rules")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, preRules)) {
                                preRules.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedPreRules =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", List.of())));
        MatcherAssert.assertThat(preRules.get(), Matchers.equalTo(expectedPreRules));

        // POST a rule definition
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("name", "Auto Rule");
        form.add("targetAlias", "es.andrewazor.demo.Main");
        form.add("description", "AutoRulesIT automated rule");
        form.add("eventSpecifier", "template=Continuous,type=TARGET");
        form.add("archivalPeriodSeconds", "60");
        form.add("preservedArchives", "3");
        webClient
                .post("/api/v2/rules")
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, postResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(201));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader("Location"),
                                        Matchers.equalTo("/api/v2/rules/Auto_Rule"));
                                postResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedPostResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                        Map.of(
                                                "type",
                                                HttpMimeType.PLAINTEXT.mime(),
                                                "status",
                                                "Created"),
                                "data", Map.of("result", "Auto_Rule")));
        MatcherAssert.assertThat(postResponse.get(), Matchers.equalTo(expectedPostResponse));

        // assert newly added rule is in total set
        CompletableFuture<JsonObject> rules = new CompletableFuture<>();
        webClient
                .get("/api/v2/rules")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, rules)) {
                                rules.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedRule =
                new JsonObject(
                        Map.of(
                                "name",
                                "Auto_Rule",
                                "description",
                                "AutoRulesIT automated rule",
                                "eventSpecifier",
                                "template=Continuous,type=TARGET",
                                "targetAlias",
                                "es.andrewazor.demo.Main",
                                "archivalPeriodSeconds",
                                60,
                                "preservedArchives",
                                3,
                                "maxAgeSeconds",
                                60,
                                "maxSizeBytes",
                                -1));
        JsonObject expectedRules =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", List.of(expectedRule))));
        MatcherAssert.assertThat(rules.get(), Matchers.equalTo(expectedRules));

        // assert newly added rule can be retrieved individually
        CompletableFuture<JsonObject> rule = new CompletableFuture<>();
        webClient
                .get("/api/v2/rules/Auto_Rule")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, rule)) {
                                rule.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedRuleResponse =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", expectedRule)));
        MatcherAssert.assertThat(rule.get(), Matchers.equalTo(expectedRuleResponse));
    }

    @Test
    @Order(2)
    void testAddRuleThrowsWhenJsonIntegerAttributesNegative() throws Exception {
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        JsonObject invalidRule = new JsonObject();
        invalidRule.put("name", "Invalid_Rule");
        invalidRule.put("description", "AutoRulesIT automated rule");
        invalidRule.put("eventSpecifier", "template=Continuous,type=TARGET");
        invalidRule.put("targetAlias", "es.andrewazor.demo.Main");
        invalidRule.put("archivalPeriodSeconds", -60);
        invalidRule.put("preservedArchives", -3);

        webClient
                .post("/api/v2/rules")
                .sendJsonObject(
                        invalidRule,
                        ar -> {
                            if (assertRequestStatus(ar, postResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(400));
                            }
                        });
    }

    @Test
    @Order(3)
    void testAddCredentials() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", "admin");
        form.add("password", "adminpass123");
        webClient
                .post(String.format("/api/v2/targets/%s/credentials", jmxServiceUrlEncoded))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.PLAINTEXT.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(response.get(), Matchers.equalTo(expectedResponse));
    }

    @Test
    @Order(4)
    void testNewContainerHasRuleApplied() throws Exception {
        CONTAINERS.add(
                Podman.run(
                        new Podman.ImageSpec(
                                "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                                Map.of("JMX_PORT", "9093", "USE_AUTH", "true"))));
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        Thread.sleep(10_000L); // wait for JDP to discover new container(s)

        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", Podman.POD_NAME + ":9093"))
                .putHeader(
                        "X-JMX-Authorization",
                        "Basic "
                                + Base64.getEncoder()
                                        .encodeToString("admin:adminpass123".getBytes()))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonObject recording = response.get().getJsonObject(0);
        MatcherAssert.assertThat(recording.getInteger("id"), Matchers.equalTo(1));
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo("auto_Auto_Rule"));
        MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("RUNNING"));
        MatcherAssert.assertThat(recording.getInteger("duration"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getInteger("maxAge"), Matchers.equalTo(60000));
        MatcherAssert.assertThat(recording.getInteger("maxSize"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getBoolean("continuous"), Matchers.equalTo(true));
        MatcherAssert.assertThat(recording.getBoolean("toDisk"), Matchers.equalTo(true));
        MatcherAssert.assertThat(
                recording.getString("downloadUrl"),
                Matchers.equalTo(
                        "http://"
                                + Utils.WEB_HOST
                                + ":"
                                + Utils.WEB_PORT
                                + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9093%2Fjmxrmi/recordings/auto_Auto_Rule"));
        MatcherAssert.assertThat(
                recording.getString("reportUrl"),
                Matchers.equalTo(
                        "http://"
                                + Utils.WEB_HOST
                                + ":"
                                + Utils.WEB_PORT
                                + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9093%2Fjmxrmi/reports/auto_Auto_Rule"));
    }

    @Test
    @Order(5)
    void testRuleCanBeDeleted() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/rules/%s", "Auto_Rule"))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.PLAINTEXT.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(response.get(), Matchers.equalTo(expectedResponse));
    }

    @Test
    @Order(6)
    void testCredentialsCanBeDeleted() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/targets/%s/credentials", jmxServiceUrlEncoded))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.PLAINTEXT.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(response.get(), Matchers.equalTo(expectedResponse));
    }
}
