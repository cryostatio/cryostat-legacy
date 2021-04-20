/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
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
 * #L%
 */
package itest;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecordingWorkflowIT extends TestBase {

    static final String TARGET_ID = "localhost";
    static final String TEST_RECORDING_NAME = "workflow_itest";

    @Test
    public void testWorkflow() throws Exception {
        // Check preconditions
        CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
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
                    .post(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
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
                    .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
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
                                    TARGET_ID, TEST_RECORDING_NAME))
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
                    .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
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
                            TARGET_ID + "_" + TEST_RECORDING_NAME + "_[\\d]{8}T[\\d]{6}Z.jfr"));
            String savedDownloadUrl = recordingInfo.getString("downloadUrl");

            Thread.sleep(3_000L); // wait for the dump to complete

            // verify the in-memory recording list has not changed, except recording is now stopped
            CompletableFuture<JsonArray> listRespFuture5 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
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
            // non-empty recording binaries (TODO: better verification of file content), and that
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
            MatcherAssert.assertThat(
                    inMemoryDownloadPath.toFile().length(),
                    Matchers.greaterThan(savedDownloadPath.toFile().length()));

            // verify that reports can be downloaded successfully and yield non-empty HTML documents
            // (TODO: verify response body is a valid HTML document)
            String reportUrl = recordingInfo.getString("reportUrl");
            Path reportPath =
                    downloadFileAbs(reportUrl, TEST_RECORDING_NAME + "_report", ".html")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(reportPath.toFile().length(), Matchers.greaterThan(0L));
        } finally {
            // Clean up what we created
            CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    TARGET_ID, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRespFuture)) {
                                    deleteRespFuture.complete(null);
                                }
                            });
            deleteRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            CompletableFuture<JsonArray> savedRecordingsFuture = new CompletableFuture<>();
            webClient
                    .get("/api/v1/recordings")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, savedRecordingsFuture)) {
                                    savedRecordingsFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            savedRecordingsFuture
                    .get()
                    .forEach(
                            rec -> {
                                String savedRecordingName = ((JsonObject) rec).getString("name");
                                if (!savedRecordingName.matches(
                                        TARGET_ID
                                                + "_"
                                                + TEST_RECORDING_NAME
                                                + "_[\\d]{8}T[\\d]{6}Z.jfr")) {
                                    return;
                                }

                                webClient
                                        .delete(
                                                String.format(
                                                        "/api/v1/recordings/%s",
                                                        savedRecordingName))
                                        .send(
                                                ar -> {
                                                    if (ar.failed()) {
                                                        ar.cause().printStackTrace();
                                                    }
                                                });
                            });
        }
    }
}
