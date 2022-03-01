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
import java.util.concurrent.TimeoutException;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RecordingMetadataIT extends StandardSelfTest {
    static JsonObject testLabels;

    static final String TARGET_ID = "localhost";
    static final String RECORDING_NAME = "Test_Recording";

    @BeforeAll
    static void setup() throws Exception {
        testLabels = new JsonObject();
        testLabels.put("KEY", "VALUE");
        testLabels.put("key.2", "some.value");
        testLabels.put("key3", "1234");
    }

    @Test
    void testStartRecordingWithLabels() throws Exception {

        try {
            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add("labels", testLabels.toString());
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

            // verify in-memory recording created with labels
            CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture)) {
                                    listRespFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            JsonObject recordingInfo = listResp.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(RECORDING_NAME));
            MatcherAssert.assertThat(
                    recordingInfo.getString("labels"), Matchers.equalTo(testLabels.toString()));

        } finally {
            // Clean up what we created
            CompletableFuture<Void> deleteRespFuture1 = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", TARGET_ID, RECORDING_NAME))
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
                        String.format("Failed to delete target recording %s", RECORDING_NAME), e);
            }
        }
    }

    @Test
    void testUpdateTargetRecordingLabels() throws Exception {

        try {
            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add("labels", testLabels.toString());
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

            // update the recording labels
            String updatedLabels =
                    "{\"KEY\":\"newValue\",\"key.2\":\"some.value\",\"key3\":\"1234\"}";
            CompletableFuture<String> patchResponse = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v2.1/targets/%s/recordings/%s/labels",
                                    TARGET_ID, RECORDING_NAME))
                    .sendBuffer(
                            Buffer.buffer(updatedLabels),
                            ar -> {
                                if (assertRequestStatus(ar, patchResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    patchResponse.complete(ar.result().bodyAsString());
                                }
                            });

            patchResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // verify in-memory recording contains updated labels
            CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture)) {
                                    listRespFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            JsonObject recordingInfo = listResp.getJsonObject(0);

            MatcherAssert.assertThat(
                    recordingInfo.getString("labels"), Matchers.equalTo(updatedLabels));

        } finally {
            // Clean up what we created
            CompletableFuture<Void> deleteRespFuture1 = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", TARGET_ID, RECORDING_NAME))
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
                        String.format("Failed to delete target recording %s", RECORDING_NAME), e);
            }
        }
    }

    @Test
    void testSaveTargetRecordingCopiesLabelsToArchivedRecording() throws Exception {
        String archivedRecordingName = null;

        try {
            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add("labels", testLabels.toString());
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

            // Save the recording to archives
            CompletableFuture<Void> saveResponse = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", TARGET_ID, RECORDING_NAME))
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveResponse)) {
                                    saveResponse.complete(null);
                                }
                            });

            MatcherAssert.assertThat(
                    saveResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    Matchers.equalTo(null));

            // verify archived recording contains labels
            CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/recordings"))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture)) {
                                    listRespFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            JsonObject recordingInfo = listResp.getJsonObject(0);
            archivedRecordingName = recordingInfo.getString("name");

            MatcherAssert.assertThat(
                    recordingInfo.getString("labels"), Matchers.equalTo(testLabels.toString()));

        } finally {
            // Clean up what we created
            CompletableFuture<Void> deleteTargetRecordingFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", TARGET_ID, RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteTargetRecordingFuture)) {
                                    deleteTargetRecordingFuture.complete(null);
                                }
                            });

            CompletableFuture<Void> deleteArchiveFuture = new CompletableFuture<>();
            webClient
                    .delete(String.format("/api/v1/recordings/%s", archivedRecordingName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteArchiveFuture)) {
                                    deleteArchiveFuture.complete(null);
                                }
                            });

            try {
                deleteTargetRecordingFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                deleteArchiveFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", RECORDING_NAME), e);
            }
        }
    }
}
