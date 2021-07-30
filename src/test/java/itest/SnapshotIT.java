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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    static final String V1_TARGET_REQ_URL =
            String.format("/api/v1/targets%s", SELF_REFERENCE_TARGET_ID);
    static final String V2_TARGET_REQ_URL =
            String.format("/api/v2/targets%s", SELF_REFERENCE_TARGET_ID);
    static final String SNAPSHOT_NAME_V1 = "snapshot-2";
    static final String SNAPSHOT_NAME_V2 = "snapshot-4";

    @AfterAll
    static void verifyRecordingsCleanedUp() throws Exception {
        CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
        webClient
                .get(String.format("%s/recordings", V1_TARGET_REQ_URL))
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

        try {
            // Create a recording
            CompletableFuture<JsonObject> recordResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");

            webClient
                    .post(String.format("%s/recordings", V1_TARGET_REQ_URL))
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
            CompletableFuture<String> snapshotResponse = new CompletableFuture<>();
            webClient
                    .post(String.format("%s/snapshot", V1_TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, snapshotResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    snapshotResponse.complete(ar.result().bodyAsString());
                                }
                            });

            MatcherAssert.assertThat(snapshotResponse.get(), Matchers.equalTo(SNAPSHOT_NAME_V1));

        } finally {
            // Clean up recording and snapshot
            CompletableFuture<JsonObject> deleteRecordingResponse = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "%s/recordings/%s", V1_TARGET_REQ_URL, TEST_RECORDING_NAME))
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
                    .delete(String.format("%s/recordings/%s", V1_TARGET_REQ_URL, SNAPSHOT_NAME_V1))
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

        try {
            // Create a recording
            CompletableFuture<JsonObject> recordResponse = new CompletableFuture<>();
            final String expectedMetaResponse =
                    "{\"meta\":{\"type\":\"text/plain\",\"status\":\"Created\"}";
            final String downloadUrl =
                    String.format(
                            "http://localhost:8181%s/recordings/%s",
                            V1_TARGET_REQ_URL, SNAPSHOT_NAME_V2);
            final String expectedDownloadUrl = String.format("\"downloadUrl\":\"%s\"", downloadUrl);
            final String expectedReportUrl =
                    String.format(
                            "\"reportUrl\":\"http://localhost:8181%s/reports/%s\"",
                            V1_TARGET_REQ_URL, SNAPSHOT_NAME_V2);
            final String expectedRecordingId =
                    String.format("\"id\":4,\"name\":\"%s\"", SNAPSHOT_NAME_V2);
            final String expectedRecordingState = "\"state\":\"STOPPED\"";
            final String expectedRecordingOptions =
                    "\"duration\":0,\"continuous\":true,\"toDisk\":true,\"maxSize\":0,\"maxAge\":0";

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");

            webClient
                    .post(String.format("%s/recordings", V1_TARGET_REQ_URL))
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
            CompletableFuture<String> snapshotResponse = new CompletableFuture<>();
            webClient
                    .post(String.format("%s/snapshot", V2_TARGET_REQ_URL))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, snapshotResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    MatcherAssert.assertThat(
                                            ar.result().getHeader("Location"),
                                            Matchers.equalTo(downloadUrl));
                                    snapshotResponse.complete(ar.result().bodyAsString());
                                }
                            });

            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedMetaResponse));
            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedDownloadUrl));
            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedReportUrl));
            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedRecordingId));
            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedRecordingState));
            MatcherAssert.assertThat(snapshotResponse.get(), Matchers.containsString("startTime"));
            MatcherAssert.assertThat(
                    snapshotResponse.get(), Matchers.containsString(expectedRecordingOptions));

        } finally {
            // Clean up recording and snapshot
            CompletableFuture<JsonObject> deleteRecordingResponse = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "%s/recordings/%s", V1_TARGET_REQ_URL, TEST_RECORDING_NAME))
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
                    .delete(String.format("%s/recordings/%s", V1_TARGET_REQ_URL, SNAPSHOT_NAME_V2))
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

        CompletableFuture<String> snapshotResponse = new CompletableFuture<>();
        webClient
                .post("/api/v2/targets/notFound:9000/snapshot")
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
}
