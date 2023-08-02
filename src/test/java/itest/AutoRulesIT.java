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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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

    final String jmxServiceUrl2 =
            String.format("service:jmx:rmi:///jndi/rmi://%s:9096/jmxrmi", Podman.POD_NAME);
    final String jmxServiceUrlEncoded2 = jmxServiceUrl2.replaceAll("/", "%2F");

    static {
        NULL_RESULT.put("result", null);
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
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
        MatcherAssert.assertThat(
                preRules.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedPreRules));

        // POST a rule definition
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("name", "Auto Rule");
        form.add(
                "matchExpression",
                "target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main'");
        form.add("description", "AutoRulesIT automated rule");
        form.add("eventSpecifier", "template=Continuous,type=TARGET");
        form.add("archivalPeriodSeconds", "60");
        form.add("initialDelaySeconds", "55");
        form.add("preservedArchives", "3");
        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
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
                                                HttpMimeType.JSON.mime(),
                                                "status",
                                                "Created"),
                                "data", Map.of("result", "Auto_Rule")));
        MatcherAssert.assertThat(
                postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedPostResponse));

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
                                "matchExpression",
                                "target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main'",
                                "archivalPeriodSeconds",
                                60,
                                "initialDelaySeconds",
                                55,
                                "preservedArchives",
                                3,
                                "maxAgeSeconds",
                                60,
                                "maxSizeBytes",
                                -1,
                                "enabled",
                                true));
        JsonObject expectedRules =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", List.of(expectedRule))));
        MatcherAssert.assertThat(
                rules.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedRules));

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
        MatcherAssert.assertThat(
                rule.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedRuleResponse));
    }

    @Test
    @Order(2)
    void testAddRuleThrowsWhenMatchExpressionIllegal() throws Exception {
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        JsonObject invalidRule = new JsonObject();
        invalidRule.put("name", "Invalid_Rule");
        invalidRule.put("description", "AutoRulesIT automated rule");
        invalidRule.put("eventSpecifier", "template=Continuous,type=TARGET");
        invalidRule.put("matchExpression", "System.exit(1)");
        invalidRule.put("archivalPeriodSeconds", -60);
        invalidRule.put("preservedArchives", -3);

        webClient
                .post("/api/v2/rules")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
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
        form.add("matchExpression", String.format("target.connectUrl == \"%s\"", jmxServiceUrl));
        webClient
                .post(String.format("/api/v2.2/credentials"))
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.URLENCODED_FORM.mime())
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
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "Created"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedResponse));
    }

    @Test
    @Order(4)
    void testNewContainerHasRuleApplied() throws Exception {

        CONTAINERS.add(
                Podman.run(
                        new Podman.ImageSpec(
                                FIB_DEMO_IMAGESPEC,
                                Map.of("JMX_PORT", "9093", "USE_AUTH", "true"))));
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(CONTAINERS.size()); // wait for JDP to discover new container(s)
        Thread.sleep(3_000L); // wait for rule activation

        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
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
        JsonObject recording =
                response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).getJsonObject(0);
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
    void testAddRuleCreatedWithRegex() throws Exception {
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        JsonObject regexRule = new JsonObject();
        regexRule.put("name", "Regex_Rule");
        regexRule.put("description", "AutoRulesIT automated rule");
        regexRule.put("eventSpecifier", "template=Continuous,type=TARGET");
        regexRule.put("matchExpression", "/[a-zA-Z0-9.]+/.test(target.alias)");
        final String expectedRecordingName = "auto_Regex_Rule";

        try {
            webClient
                    .post("/api/v2/rules")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .sendJsonObject(
                            regexRule,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    postResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject expectedPostResponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                            Map.of(
                                                    "type",
                                                    HttpMimeType.JSON.mime(),
                                                    "status",
                                                    "Created"),
                                    "data", Map.of("result", "Regex_Rule")));
            MatcherAssert.assertThat(
                    postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    Matchers.equalTo(expectedPostResponse));

            // give rule some time to process. Five seconds should be massively overkill, but better
            // to give extra time and have a reliable test than try to time it quickly and have a
            // flakey test. Even better would be to listen for the WebSocket notification that
            // confirms that the recordings created by the rule have been created and then continue
            Thread.sleep(5_000);

            // Assert rule applied to both targets
            CompletableFuture<JsonArray> getResponse = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResponse)) {
                                    getResponse.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonObject recording =
                    getResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).getJsonObject(1);
            MatcherAssert.assertThat(recording.getInteger("id"), Matchers.equalTo(2));
            MatcherAssert.assertThat(
                    recording.getString("name"), Matchers.equalTo(expectedRecordingName));
            MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("RUNNING"));
            MatcherAssert.assertThat(recording.getInteger("duration"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording.getInteger("maxAge"), Matchers.equalTo(0));
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
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9093%2Fjmxrmi/recordings/auto_Regex_Rule"));
            MatcherAssert.assertThat(
                    recording.getString("reportUrl"),
                    Matchers.equalTo(
                            "http://"
                                    + Utils.WEB_HOST
                                    + ":"
                                    + Utils.WEB_PORT
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9093%2Fjmxrmi/reports/auto_Regex_Rule"));

            CompletableFuture<JsonArray> getResponse2 = new CompletableFuture<>();
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    jmxServiceUrlEncoded.replace("9093", "9091")))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResponse2)) {
                                    getResponse2.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonObject recording2 =
                    getResponse2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).getJsonObject(0);
            MatcherAssert.assertThat(recording.getInteger("id"), Matchers.equalTo(2));
            MatcherAssert.assertThat(
                    recording2.getString("name"), Matchers.equalTo(expectedRecordingName));
            MatcherAssert.assertThat(recording2.getString("state"), Matchers.equalTo("RUNNING"));
            MatcherAssert.assertThat(recording2.getInteger("duration"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getInteger("maxAge"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getInteger("maxSize"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getBoolean("continuous"), Matchers.equalTo(true));
            MatcherAssert.assertThat(recording2.getBoolean("toDisk"), Matchers.equalTo(true));
            MatcherAssert.assertThat(
                    recording2.getString("downloadUrl"),
                    Matchers.equalTo(
                            "http://"
                                    + Utils.WEB_HOST
                                    + ":"
                                    + Utils.WEB_PORT
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9091%2Fjmxrmi/recordings/auto_Regex_Rule"));
            MatcherAssert.assertThat(
                    recording2.getString("reportUrl"),
                    Matchers.equalTo(
                            "http://"
                                    + Utils.WEB_HOST
                                    + ":"
                                    + Utils.WEB_PORT
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9091%2Fjmxrmi/reports/auto_Regex_Rule"));

        } finally {

            // Delete the rule
            CompletableFuture<JsonObject> response = new CompletableFuture<>();
            webClient
                    .delete(String.format("/api/v2/rules/%s", "Regex_Rule"))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, response)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    response.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            // Delete active recordings generated by the above rule
            CompletableFuture<JsonObject> deleteFibDemoRecResponse = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    jmxServiceUrlEncoded, expectedRecordingName))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteFibDemoRecResponse)) {
                                    deleteFibDemoRecResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteFibDemoRecResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format(
                                "Failed to delete target recording %s", expectedRecordingName),
                        e);
            }

            CompletableFuture<JsonObject> deleteCryostatRecResponse = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    jmxServiceUrlEncoded.replace("9093", "9091"),
                                    expectedRecordingName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteCryostatRecResponse)) {
                                    deleteCryostatRecResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteCryostatRecResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format(
                                "Failed to delete target recording %s", expectedRecordingName),
                        e);
            }
        }
    }

    @Test
    @Order(6)
    void testRuleCanBeDeleted() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/rules/%s?clean=true", "Auto_Rule"))
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
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedResponse));
    }

    @Test
    @Order(7)
    void testAddRuleDisabledAndPatchEnable() throws Exception {
        final String ruleName = "Disabled_Rule";
        final String recordingName = "auto_Disabled_Rule";

        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("name", ruleName);
        form.add("matchExpression", "target.annotations.cryostat.PORT == 9096");
        form.add("description", "AutoRulesIT automated rule created disabled");
        form.add("eventSpecifier", "template=Continuous,type=TARGET");
        form.add("enabled", "false");

        String containerId = "";

        try {

            webClient
                    .post("/api/v2/rules")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    postResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject expectedPostResponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                            Map.of(
                                                    "type",
                                                    HttpMimeType.JSON.mime(),
                                                    "status",
                                                    "Created"),
                                    "data", Map.of("result", ruleName)));
            MatcherAssert.assertThat(
                    postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    Matchers.equalTo(expectedPostResponse));

            containerId =
                    Podman.run(
                            new Podman.ImageSpec(FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", "9096")));
            // add a new target
            CONTAINERS.add(containerId);
            waitForDiscovery(CONTAINERS.size());

            CompletableFuture<JsonArray> getResp = new CompletableFuture<>();

            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded2))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResp)) {
                                    getResp.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            MatcherAssert.assertThat(
                    getResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).size(), Matchers.is(0));

            JsonObject enableObject = new JsonObject();
            enableObject.put("enabled", true);

            CompletableFuture<JsonObject> patchResp = new CompletableFuture<>();

            webClient
                    .patch(String.format("/api/v2/rules/%s", ruleName))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                    .sendJson(
                            enableObject,
                            ar -> {
                                if (assertRequestStatus(ar, patchResp)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(204));
                                    patchResp.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            patchResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Thread.sleep(3_000);

            CompletableFuture<JsonArray> getResp2 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded2))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResp2)) {
                                    getResp2.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            JsonArray postEnableRecordings =
                    getResp2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(postEnableRecordings.size(), Matchers.equalTo(1));
            JsonObject recording2 = postEnableRecordings.getJsonObject(0);
            MatcherAssert.assertThat(recording2.getInteger("id"), Matchers.equalTo(1));
            MatcherAssert.assertThat(recording2.getString("name"), Matchers.equalTo(recordingName));
            MatcherAssert.assertThat(recording2.getString("state"), Matchers.equalTo("RUNNING"));
            MatcherAssert.assertThat(recording2.getInteger("duration"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getInteger("maxAge"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getInteger("maxSize"), Matchers.equalTo(0));
            MatcherAssert.assertThat(recording2.getBoolean("continuous"), Matchers.equalTo(true));
            MatcherAssert.assertThat(recording2.getBoolean("toDisk"), Matchers.equalTo(true));
            MatcherAssert.assertThat(
                    recording2.getString("downloadUrl"),
                    Matchers.equalTo(
                            "http://"
                                    + Utils.WEB_HOST
                                    + ":"
                                    + Utils.WEB_PORT
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9096%2Fjmxrmi/recordings/"
                                    + recordingName));
            MatcherAssert.assertThat(
                    recording2.getString("reportUrl"),
                    Matchers.equalTo(
                            "http://"
                                    + Utils.WEB_HOST
                                    + ":"
                                    + Utils.WEB_PORT
                                    + "/api/v1/targets/service:jmx:rmi:%2F%2F%2Fjndi%2Frmi:%2F%2Fcryostat-itests:9096%2Fjmxrmi/reports/"
                                    + recordingName));

        } finally {

            // delete the test rule
            CompletableFuture<JsonObject> deleteRuleResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("/api/v2/rules/%s", ruleName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRuleResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteRuleResponse.complete(ar.result().bodyAsJsonObject());
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
                        deleteRuleResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        Matchers.equalTo(expectedDeleteResponse));
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete rule %s", ruleName), e);
            }

            // tear down the target
            Podman.kill(containerId);
            CONTAINERS.remove(containerId);
            waitForDiscovery(CONTAINERS.size());
        }
    }

    @Test
    @Order(8)
    void testCredentialsCanBeDeleted() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get("/api/v2.2/credentials")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject query = getResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonObject data = query.getJsonObject("data");
        JsonArray result = data.getJsonArray("result");
        MatcherAssert.assertThat(result.size(), Matchers.equalTo(1));
        JsonObject cred = result.getJsonObject(0);
        int id = cred.getInteger("id");

        CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2.2/credentials/%d", id))
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
        MatcherAssert.assertThat(
                deleteResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedDeleteResponse));
    }

    @Test
    @Order(9)
    void testGetNonExistentRuleThrows() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .get("/api/v2/rules/Auto_Rule")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });

        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    @Order(10)
    void testDeleteNonExistentRuleThrows() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete("/api/v2/rules/Auto_Rule")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });

        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    @Order(11)
    void testNoRulesAfterRun() throws Exception {
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
        MatcherAssert.assertThat(
                preRules.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedPreRules));
    }
}
