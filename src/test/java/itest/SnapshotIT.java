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

import java.util.LinkedHashMap;
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
import io.vertx.ext.web.handler.impl.HttpStatusException;
import itest.bases.StandardSelfTest;
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

            MatcherAssert.assertThat(deleteRecordingResponse.get(), Matchers.equalTo(null));

            CompletableFuture<JsonObject> deleteSnapshotResponse = new CompletableFuture<>();

            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, snapshotName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteSnapshotResponse)) {
                                    deleteSnapshotResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            MatcherAssert.assertThat(deleteSnapshotResponse.get(), Matchers.equalTo(null));
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
                ((HttpStatusException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
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

            LinkedHashMap<String, Object> expectedResult = new LinkedHashMap<String, Object>();
            expectedResult.put("downloadUrl", expectedDownloadUrl);
            expectedResult.put("reportUrl", expectedReportUrl);
            expectedResult.put("id", snapshotId);
            expectedResult.put("name", snapshotName.get());
            expectedResult.put("state", "STOPPED");
            expectedResult.put("startTime", startTime);
            expectedResult.put("duration", 0);
            expectedResult.put("continuous", true);
            expectedResult.put("toDisk", true);
            expectedResult.put("maxSize", 0);
            expectedResult.put("maxAge", 0);

            JsonObject expectedCreateResponse =
                    new JsonObject(
                            Map.of(
                                    "meta",
                                            Map.of(
                                                    "type",
                                                    HttpMimeType.PLAINTEXT.mime(),
                                                    "status",
                                                    "Created"),
                                    "data", Map.of("result", expectedResult)));

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

            MatcherAssert.assertThat(deleteRecordingResponse.get(), Matchers.equalTo(null));

            CompletableFuture<JsonObject> deleteSnapshotResponse = new CompletableFuture<>();

            webClient
                    .delete(String.format("%s/recordings/%s", TARGET_REQ_URL, snapshotName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteSnapshotResponse)) {
                                    deleteSnapshotResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            MatcherAssert.assertThat(deleteSnapshotResponse.get(), Matchers.equalTo(null));
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
                ((HttpStatusException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
