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
        String name = "myrule";
        form.add("enabled", "true");
        form.add("name", name);
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
                                        Matchers.equalTo("/api/v2/rules/" + name));
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
                                "data", Map.of("result", name)));
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
                                name,
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
    public void testAddRuleTriggersRecordingCreation()
            throws TimeoutException, InterruptedException, ExecutionException {
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
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo("auto_myrule"));
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
    public void testDeleteRule() throws TimeoutException, InterruptedException, ExecutionException {
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
    @Order(4)
    public void testCleanedUp() throws TimeoutException, InterruptedException, ExecutionException {
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
        MatcherAssert.assertThat(recording.getString("name"), Matchers.equalTo("auto_myrule"));
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
