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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.net.web.http.HttpMimeType;

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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
            Path reportPath =
                    downloadFileAbs(reportUrl, TEST_RECORDING_NAME + "_report", ".html")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            File reportFile = reportPath.toFile();
            MatcherAssert.assertThat(reportFile.length(), Matchers.greaterThan(0L));
            Document doc = Jsoup.parse(reportFile, "UTF-8");

            Elements head = doc.getElementsByTag("head");
            Elements titles = head.first().getElementsByTag("title");
            Elements body = doc.getElementsByTag("body");
            Elements script = head.first().getElementsByTag("script");

            MatcherAssert.assertThat("Expected one <head>", head.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat(titles.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat(
                    "Expected at least one <script>",
                    script.size(),
                    Matchers.greaterThanOrEqualTo(1));

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
