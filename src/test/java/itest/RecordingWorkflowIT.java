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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.net.web.http.HttpMimeType;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecordingWorkflowIT extends StandardSelfTest {

    static final String TEST_RECORDING_NAME = "workflow_itest";
    static final String TARGET_ALIAS = "io-cryostat-Cryostat";

    @Test
    public void testWorkflow() throws Exception {
        // Check preconditions
        CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFuture1)) {
                                listRespFuture1.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listResp = listRespFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assertions.assertTrue(listResp.isEmpty());

        try {
            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, dumpRespFuture)) {
                                    dumpRespFuture.complete(null);
                                }
                            });
            dumpRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // verify in-memory recording created
            CompletableFuture<JsonArray> listRespFuture2 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture2)) {
                                    listRespFuture2.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            listResp = listRespFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            MatcherAssert.assertThat(
                    "list should have size 1 after recording creation",
                    listResp.size(),
                    Matchers.equalTo(1));
            JsonObject recordingInfo = listResp.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("RUNNING"));

            Thread.sleep(2_000L); // wait some time to save a portion of the recording

            // save a copy of the partial recording dump
            CompletableFuture<Void> saveRespFuture = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, TEST_RECORDING_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveRespFuture)) {
                                    saveRespFuture.complete(null);
                                }
                            });
            saveRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // check that the in-memory recording list hasn't changed
            CompletableFuture<JsonArray> listRespFuture3 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture3)) {
                                    listRespFuture3.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            listResp = listRespFuture3.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            MatcherAssert.assertThat(
                    "list should have size 1 after recording creation",
                    listResp.size(),
                    Matchers.equalTo(1));
            recordingInfo = listResp.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("RUNNING"));

            // verify saved recording created
            CompletableFuture<JsonArray> listRespFuture4 = new CompletableFuture<>();
            webClient
                    .get("/api/v1/recordings")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture4)) {
                                    listRespFuture4.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            listResp = listRespFuture4.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            MatcherAssert.assertThat(
                    "list-saved should have size 1 after recording save",
                    listResp.size(),
                    Matchers.equalTo(1));
            recordingInfo = listResp.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"),
                    Matchers.matchesRegex(
                            TARGET_ALIAS + "_" + TEST_RECORDING_NAME + "_[\\d]{8}T[\\d]{6}Z.jfr"));
            String savedDownloadUrl = recordingInfo.getString("downloadUrl");

            Thread.sleep(3_000L); // wait for the dump to complete

            // verify the in-memory recording list has not changed, except recording is now stopped
            CompletableFuture<JsonArray> listRespFuture5 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture5)) {
                                    listRespFuture5.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            listResp = listRespFuture5.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(
                    "list should have size 1 after wait period",
                    listResp.size(),
                    Matchers.equalTo(1));
            recordingInfo = listResp.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("STOPPED"));
            MatcherAssert.assertThat(recordingInfo.getInteger("duration"), Matchers.equalTo(5_000));

            // verify in-memory and saved recordings can be downloaded successfully and yield
            // non-empty recording binaries containing events, and that
            // the fully completed in-memory recording is larger than the saved partial copy
            String inMemoryDownloadUrl = recordingInfo.getString("downloadUrl");
            Path inMemoryDownloadPath =
                    downloadFileAbs(inMemoryDownloadUrl, TEST_RECORDING_NAME, ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Path savedDownloadPath =
                    downloadFileAbs(savedDownloadUrl, TEST_RECORDING_NAME + "_saved", ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(
                    inMemoryDownloadPath.toFile().length(), Matchers.greaterThan(0L));
            MatcherAssert.assertThat(savedDownloadPath.toFile().length(), Matchers.greaterThan(0L));

            List<RecordedEvent> inMemoryEvents = RecordingFile.readAllEvents(inMemoryDownloadPath);
            List<RecordedEvent> savedEvents = RecordingFile.readAllEvents(savedDownloadPath);

            MatcherAssert.assertThat(
                    inMemoryEvents.size(), Matchers.greaterThan(savedEvents.size()));

            String reportUrl = recordingInfo.getString("reportUrl");
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add(HttpHeaders.ACCEPT.toString(), HttpMimeType.JSON.mime());
            Path reportPath =
                    downloadFileAbs(reportUrl, TEST_RECORDING_NAME + "_report", ".json", headers)
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            File reportFile = reportPath.toFile();
            MatcherAssert.assertThat(reportFile.length(), Matchers.greaterThan(0L));

            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> response = mapper.readValue(reportFile, Map.class);
            MatcherAssert.assertThat(response, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    response, Matchers.is(Matchers.aMapWithSize(Matchers.greaterThan(8))));

        } finally {
            // Clean up what we created
            CompletableFuture<Void> deleteRespFuture1 = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRespFuture1)) {
                                    deleteRespFuture1.complete(null);
                                }
                            });

            try {
                deleteRespFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", TEST_RECORDING_NAME),
                        e);
            }

            CompletableFuture<JsonArray> savedRecordingsFuture = new CompletableFuture<>();
            webClient
                    .get("/api/v1/recordings")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, savedRecordingsFuture)) {
                                    savedRecordingsFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            JsonArray savedRecordings = null;
            try {
                savedRecordings =
                        savedRecordingsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException("Failed to retrieve archived recordings", e);
            }

            for (Object savedRecording : savedRecordings) {
                String recordingName = ((JsonObject) savedRecording).getString("name");
                if (recordingName.matches(
                        TARGET_ALIAS + "_" + TEST_RECORDING_NAME + "_[\\d]{8}T[\\d]{6}Z.jfr")) {
                    CompletableFuture<Void> deleteRespFuture2 = new CompletableFuture<>();
                    webClient
                            .delete(
                                    String.format(
                                            "/api/beta/recordings/%s/%s",
                                            SELF_REFERENCE_TARGET_ID, recordingName))
                            .send(
                                    ar -> {
                                        if (assertRequestStatus(ar, deleteRespFuture2)) {
                                            deleteRespFuture2.complete(null);
                                        }
                                    });
                    try {
                        deleteRespFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        throw new ITestCleanupFailedException(
                                String.format(
                                        "Failed to delete archived recording %s", recordingName),
                                e);
                    }
                }
            }
        }
    }
}
