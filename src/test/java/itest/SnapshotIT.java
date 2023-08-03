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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SnapshotIT extends StandardSelfTest {
    static final String TEST_RECORDING_NAME = "someRecording";
    static final String TARGET_REQ_URL =
            String.format("/api/v1/targets%s", SELF_REFERENCE_TARGET_ID);
    static final String V2_SNAPSHOT_REQ_URL =
            String.format("/api/v2/targets%s", SELF_REFERENCE_TARGET_ID);
    static final Pattern SNAPSHOT_NAME_PATTERN = Pattern.compile("^snapshot-[0-9]+$");
    static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^[0-9]+$");

    @AfterAll
    static void verifyRecordingsCleanedUp() throws Exception {
        CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
        webClient
                .get(String.format("%s/recordings", TARGET_REQ_URL))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFuture1)) {
                                listRespFuture1.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listResp = listRespFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assertions.assertTrue(listResp.isEmpty());
    }

    @Test
    void testPostV1ShouldCreateSnapshot() throws Exception {

        CompletableFuture<String> snapshotName = new CompletableFuture<>();

        try {
            // Create a recording
            CompletableFuture<JsonObject> recordResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");

            webClient
                    .post(String.format("%s/recordings", TARGET_REQ_URL))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, recordResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    recordResponse.complete(null);
                                }
                            });

            recordResponse.get();

            // Create a snapshot recording of all events at that time
            webClient
                    .post(String.format("%s/snapshot", TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, snapshotName)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    snapshotName.complete(ar.result().bodyAsString());
                                }
                            });

            MatcherAssert.assertThat(
                    snapshotName.get(), Matchers.matchesPattern(SNAPSHOT_NAME_PATTERN));

        } finally {
            // Clean up recording and snapshot
            CompletableFuture<JsonObject> deleteRecordingResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRecordingResponse)) {
                                    deleteRecordingResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteRecordingResponse.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", TEST_RECORDING_NAME),
                        e);
            }

            CompletableFuture<JsonObject> deleteSnapshotResponse = new CompletableFuture<>();

            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, snapshotName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteSnapshotResponse)) {
                                    deleteSnapshotResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteSnapshotResponse.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete snapshot %s", snapshotName.get()), e);
            }
        }
    }

    @Test
    void testPostV1ShouldHandleEmptySnapshot() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();

        try {
            // Create an empty snapshot recording (no active recordings present)
            webClient
                    .post(String.format("%s/snapshot", TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, result)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(202));
                                    result.complete(null);
                                }
                            });
            result.get();
        } finally {
            // The empty snapshot should've been deleted (i.e. there should be no recordings
            // present)
            CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("%s/recordings", TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture)) {
                                    listRespFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture.get();
            Assertions.assertTrue(listResp.isEmpty());
        }
    }

    @Test
    void testPostV1SnapshotThrowsWithNonExistentTarget() throws Exception {

        CompletableFuture<String> snapshotResponse = new CompletableFuture<>();
        webClient
                .post("/api/v1/targets/notFound:9000/snapshot")
                .send(
                        ar -> {
                            assertRequestStatus(ar, snapshotResponse);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> snapshotResponse.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    void testPostV2ShouldCreateSnapshot() throws Exception {

        CompletableFuture<String> snapshotName = new CompletableFuture<>();

        try {
            // Create a recording
            CompletableFuture<JsonObject> recordResponse = new CompletableFuture<>();

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");

            webClient
                    .post(String.format("%s/recordings", TARGET_REQ_URL))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, recordResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    recordResponse.complete(null);
                                }
                            });

            recordResponse.get();

            // Create a snapshot recording of all events at that time
            CompletableFuture<JsonObject> createResponse = new CompletableFuture<>();
            webClient
                    .post(String.format("%s/snapshot", V2_SNAPSHOT_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, createResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    createResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            snapshotName.complete(
                    createResponse
                            .get()
                            .getJsonObject("data")
                            .getJsonObject("result")
                            .getString("name"));

            final Long startTime =
                    createResponse
                            .get()
                            .getJsonObject("data")
                            .getJsonObject("result")
                            .getLong("startTime");

            final Long duration =
                    createResponse
                            .get()
                            .getJsonObject("data")
                            .getJsonObject("result")
                            .getLong("duration");

            // Extract id from snapshot name for validation
            Pattern idPattern = Pattern.compile("[0-9]+");
            Matcher idMatcher = idPattern.matcher(snapshotName.get());
            idMatcher.find();
            final Integer snapshotId = Integer.parseInt(idMatcher.group());

            final String expectedDownloadUrl =
                    String.format(
                            "http://localhost:8181%s/recordings/%s",
                            TARGET_REQ_URL, snapshotName.get());
            final String expectedReportUrl =
                    String.format(
                            "http://localhost:8181%s/reports/%s",
                            TARGET_REQ_URL, snapshotName.get());

            JsonObject expectedCreateResponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                            Map.of(
                                                    "type",
                                                    HttpMimeType.PLAINTEXT.mime(),
                                                    "status",
                                                    "Created"),
                                    "data",
                                            Map.ofEntries(
                                                    Map.entry(
                                                            "result",
                                                            Map.ofEntries(
                                                                    Map.entry(
                                                                            "downloadUrl",
                                                                            expectedDownloadUrl),
                                                                    Map.entry(
                                                                            "reportUrl",
                                                                            expectedReportUrl),
                                                                    Map.entry("id", snapshotId),
                                                                    Map.entry(
                                                                            "name",
                                                                            snapshotName.get()),
                                                                    Map.entry("state", "STOPPED"),
                                                                    Map.entry(
                                                                            "startTime", startTime),
                                                                    Map.entry("duration", duration),
                                                                    Map.entry("continuous", true),
                                                                    Map.entry("toDisk", true),
                                                                    Map.entry("maxSize", 0),
                                                                    Map.entry("maxAge", 0),
                                                                    Map.entry(
                                                                            "archiveOnStop", false),
                                                                    Map.entry(
                                                                            "metadata",
                                                                            Map.of(
                                                                                    "labels",
                                                                                    Map.of())))))));

            MatcherAssert.assertThat(
                    createResponse.get(), Matchers.equalToObject(expectedCreateResponse));

        } finally {
            // Clean up recording and snapshot
            CompletableFuture<JsonObject> deleteRecordingResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRecordingResponse)) {
                                    deleteRecordingResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteRecordingResponse.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", TEST_RECORDING_NAME),
                        e);
            }

            CompletableFuture<JsonObject> deleteSnapshotResponse = new CompletableFuture<>();

            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, snapshotName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteSnapshotResponse)) {
                                    deleteSnapshotResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteSnapshotResponse.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete snapshot %s", snapshotName.get()), e);
            }
        }
    }

    @Test
    void testPostV2ShouldHandleEmptySnapshot() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();

        try {
            // Create an empty snapshot recording (no active recordings present)
            webClient
                    .post(String.format("%s/snapshot", V2_SNAPSHOT_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, result)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(202));
                                    result.complete(null);
                                }
                            });
            result.get();
        } finally {
            // The empty snapshot should've been deleted (i.e. there should be no recordings
            // present)
            CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("%s/recordings", TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture)) {
                                    listRespFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture.get();
            Assertions.assertTrue(listResp.isEmpty());
        }
    }

    @Test
    void testPostV2SnapshotThrowsWithNonExistentTarget() throws Exception {

        CompletableFuture<String> snapshotName = new CompletableFuture<>();
        webClient
                .post("/api/v2/targets/notFound:9000/snapshot")
                .send(
                        ar -> {
                            assertRequestStatus(ar, snapshotName);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> snapshotName.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
