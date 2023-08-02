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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.bouncycastle.util.Longs;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UploadRecordingIT extends StandardSelfTest {

    static final String RECORDING_NAME = "upload_recording_it_rec";
    static final int RECORDING_DURATION_SECONDS = 10;

    @BeforeAll
    public static void createRecording() throws Exception {
        CompletableFuture<JsonObject> dumpPostResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", RECORDING_NAME);
        form.add("duration", String.valueOf(RECORDING_DURATION_SECONDS));
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, dumpPostResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(201));
                                dumpPostResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        dumpPostResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(
                Long.valueOf(
                        RECORDING_DURATION_SECONDS * 1000)); // Wait for the recording to finish
    }

    @AfterAll
    public static void deleteRecording() throws Exception {
        CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
        webClient
                .delete(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s",
                                SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, deleteRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                deleteRespFuture.complete(null);
                            }
                        });
        try {
            deleteRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ITestCleanupFailedException(
                    String.format("Failed to delete target recording %s", RECORDING_NAME), e);
        }
    }

    @Test
    public void shouldLoadRecordingToDatasource() throws Exception {
        final CompletableFuture<String> uploadRespFuture = new CompletableFuture<>();
        webClient
                .post(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s/upload",
                                SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, uploadRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                            }
                            uploadRespFuture.complete(ar.result().bodyAsString());
                        });

        final String expectedUploadResponse =
                String.format(
                        "Uploaded: %s\nSet: %s",
                        WebServer.DATASOURCE_FILENAME, WebServer.DATASOURCE_FILENAME);

        MatcherAssert.assertThat(
                uploadRespFuture.get().trim(), Matchers.equalTo(expectedUploadResponse));

        // Confirm recording is loaded in Data Source
        final CompletableFuture<String> getRespFuture = new CompletableFuture<>();
        webClient
                .get(8080, "localhost", "/list")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                getRespFuture.complete(ar.result().bodyAsString());
                            }
                        });

        MatcherAssert.assertThat(
                getRespFuture.get().trim(),
                Matchers.equalTo(String.format("**%s**", WebServer.DATASOURCE_FILENAME)));

        // Query Data Source for recording metrics
        final CompletableFuture<JsonArray> queryRespFuture = new CompletableFuture<>();

        Instant toDate = Instant.now(); // Capture the current moment in UTC
        Instant fromDate = toDate.plusSeconds(-REQUEST_TIMEOUT_SECONDS);
        final String FROM = fromDate.toString();
        final String TO = toDate.toString();
        final String TARGET_METRIC = "jdk.CPULoad.machineTotal";

        final JsonObject query =
                new JsonObject(
                        Map.ofEntries(
                                Map.entry("app", "dashboard"),
                                Map.entry("dashboardId", 1), // Main Dashboard
                                Map.entry("panelId", 3), // Any ID
                                Map.entry("requestId", "Q237"), // Some request ID
                                Map.entry("timezone", "browser"),
                                Map.entry(
                                        "range",
                                        Map.of(
                                                "from",
                                                FROM,
                                                "to",
                                                TO,
                                                "raw",
                                                Map.of("from", FROM, "to", TO))),
                                Map.entry("interval", "1s"),
                                Map.entry("intervalMs", "1000"),
                                Map.entry(
                                        "targets",
                                        List.of(
                                                Map.of(
                                                        "target",
                                                        TARGET_METRIC,
                                                        "refId",
                                                        "A",
                                                        "type",
                                                        "timeserie"))),
                                Map.entry("maxDataPoints", 1000),
                                Map.entry("adhocFilters", List.of())));

        webClient
                .post(8080, "localhost", "/query")
                .sendJsonObject(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, queryRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                queryRespFuture.complete(ar.result().bodyAsJsonArray());
                            }
                        });

        final JsonArray arrResponse = queryRespFuture.get();
        MatcherAssert.assertThat(arrResponse, Matchers.notNullValue());
        MatcherAssert.assertThat(arrResponse.size(), Matchers.equalTo(1)); // Single target

        JsonObject targetResponse = arrResponse.getJsonObject(0);
        MatcherAssert.assertThat(
                targetResponse.getString("target"), Matchers.equalTo(TARGET_METRIC));
        MatcherAssert.assertThat(
                targetResponse.getJsonObject("meta"), Matchers.equalTo(new JsonObject()));

        JsonArray dataPoints = targetResponse.getJsonArray("datapoints");
        MatcherAssert.assertThat(dataPoints, Matchers.notNullValue());

        for (Object dataPoint : dataPoints) {
            JsonArray datapointPair = (JsonArray) dataPoint;
            MatcherAssert.assertThat(
                    datapointPair.size(), Matchers.equalTo(2)); // [value, timestamp]
            MatcherAssert.assertThat(
                    datapointPair.getDouble(0), Matchers.notNullValue()); // value must not be null
            MatcherAssert.assertThat(
                    datapointPair.getLong(1),
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.greaterThan(
                                    Longs.valueOf(0)))); // timestamp must be non-null and positive
        }
    }
}
