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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class AutoRulesCleanupIT extends ExternalTargetsTest {

    static final List<String> CONTAINERS = new ArrayList<>();
    static final Map<String, String> NULL_RESULT = new HashMap<>();

    final String jmxServiceUrl =
            String.format("service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME);
    final String jmxServiceUrlEncoded = jmxServiceUrl.replaceAll("/", "%2F");

    final String ruleName = "myrule";
    final String recordingName = "auto_myrule";

    static {
        NULL_RESULT.put("result", null);
    }

    @BeforeAll
    static void setup() throws Exception {
        Podman.ImageSpec spec =
                new Podman.ImageSpec(FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", String.valueOf(9093)));
        CONTAINERS.add(Podman.run(spec));
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(CONTAINERS.size());
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
    void testAddRule() throws TimeoutException, InterruptedException, ExecutionException {
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

        form.add("enabled", "true");
        form.add("name", ruleName);
        form.add(
                "matchExpression",
                "target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main'");
        form.add("description", "");
        form.add("eventSpecifier", "template=Continuous,type=TARGET");
        form.add("initialDelaySeconds", "0");
        form.add("archivalPeriodSeconds", "0");
        form.add("preservedArchives", "0");
        form.add("maxAgeSeconds", "0");
        form.add("maxSizeBytes", "0");
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
                                        Matchers.equalTo("/api/v2/rules/" + ruleName));
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
                                ruleName,
                                "description",
                                "",
                                "eventSpecifier",
                                "template=Continuous,type=TARGET",
                                "matchExpression",
                                "target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main'",
                                "archivalPeriodSeconds",
                                0,
                                "initialDelaySeconds",
                                0,
                                "preservedArchives",
                                0,
                                "maxAgeSeconds",
                                0,
                                "maxSizeBytes",
                                0,
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
    }

    @Test
    @Order(2)
    void testAddRuleTriggersRecordingCreation()
            throws TimeoutException, InterruptedException, ExecutionException {
        Thread.sleep(3_000); // Wait for rule to trigger

        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray recordings = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(recordings.size(), Matchers.equalTo(1));
        JsonObject recording = recordings.getJsonObject(0);
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo(recordingName));
        MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("RUNNING"));
        Assertions.assertTrue(recording.getBoolean("continuous"));
        Assertions.assertTrue(recording.getBoolean("toDisk"));
        Assertions.assertFalse(recording.getBoolean("archiveOnStop"));
        MatcherAssert.assertThat(recording.getNumber("maxAge"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getNumber("maxSize"), Matchers.equalTo(0));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.name"),
                Matchers.equalTo("Continuous"));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.type"),
                Matchers.equalTo("TARGET"));
    }

    @Test
    @Order(3)
    void testDisableRuleWithCleanup()
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonObject> patchResp = new CompletableFuture<>();

        JsonObject disabledObj = new JsonObject();
        disabledObj.put("enabled", false);

        webClient
                .patch(String.format("/api/v2/rules/%s?clean=true", ruleName))
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                .sendJson(
                        disabledObj,
                        ar -> {
                            if (assertRequestStatus(ar, patchResp)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(204));
                                patchResp.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        patchResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Thread.sleep(3_000); // Wait to stop recording

        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray recordings = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(recordings.size(), Matchers.equalTo(1));
        JsonObject recording = recordings.getJsonObject(0);
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo(recordingName));
        MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("STOPPED"));
        Assertions.assertTrue(recording.getBoolean("continuous"));
        Assertions.assertTrue(recording.getBoolean("toDisk"));
        Assertions.assertFalse(recording.getBoolean("archiveOnStop"));
        MatcherAssert.assertThat(recording.getNumber("maxAge"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getNumber("maxSize"), Matchers.equalTo(0));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.name"),
                Matchers.equalTo("Continuous"));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.type"),
                Matchers.equalTo("TARGET"));
    }

    @Test
    @Order(4)
    void testEnableRuleSetRecordingStateToRunning()
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonObject> patchResp = new CompletableFuture<>();

        JsonObject disabledObj = new JsonObject();
        disabledObj.put("enabled", true);

        webClient
                .patch(String.format("/api/v2/rules/%s?clean=true", ruleName))
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.JSON.mime())
                .sendJson(
                        disabledObj,
                        ar -> {
                            if (assertRequestStatus(ar, patchResp)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(204));
                                patchResp.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        patchResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Thread.sleep(3_000); // Wait to restart recording

        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray recordings = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(recordings.size(), Matchers.equalTo(1));
        JsonObject recording = recordings.getJsonObject(0);
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo(recordingName));
        MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("RUNNING"));
        Assertions.assertTrue(recording.getBoolean("continuous"));
        Assertions.assertTrue(recording.getBoolean("toDisk"));
        Assertions.assertFalse(recording.getBoolean("archiveOnStop"));
        MatcherAssert.assertThat(recording.getNumber("maxAge"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getNumber("maxSize"), Matchers.equalTo(0));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.name"),
                Matchers.equalTo("Continuous"));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.type"),
                Matchers.equalTo("TARGET"));
    }

    @Test
    @Order(5)
    void testDeleteRule() throws TimeoutException, InterruptedException, ExecutionException {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/rules/%s", "myrule"))
                .addQueryParam("clean", "true")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                response.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(1_000); // allow some time for async cleanup handling
    }

    @Test
    @Order(6)
    void testCleanedUp() throws TimeoutException, InterruptedException, ExecutionException {
        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray recordings = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(recordings.size(), Matchers.equalTo(1));
        JsonObject recording = recordings.getJsonObject(0);
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo(recordingName));
        MatcherAssert.assertThat(recording.getString("state"), Matchers.equalTo("STOPPED"));
        Assertions.assertTrue(recording.getBoolean("continuous"));
        Assertions.assertTrue(recording.getBoolean("toDisk"));
        Assertions.assertFalse(recording.getBoolean("archiveOnStop"));
        MatcherAssert.assertThat(recording.getNumber("maxAge"), Matchers.equalTo(0));
        MatcherAssert.assertThat(recording.getNumber("maxSize"), Matchers.equalTo(0));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.name"),
                Matchers.equalTo("Continuous"));
        MatcherAssert.assertThat(
                recording
                        .getJsonObject("metadata")
                        .getJsonObject("labels")
                        .getString("template.type"),
                Matchers.equalTo("TARGET"));
    }
}
