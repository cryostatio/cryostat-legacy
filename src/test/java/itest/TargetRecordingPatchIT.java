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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TargetRecordingPatchIT extends StandardSelfTest {
    static final String TEST_RECORDING_NAME = "someRecording";
    static final String RECORDING_REQ_URL =
            String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID);
    static final String ARCHIVED_REQ_URL = "/api/v1/recordings";
    static final String OPTIONS_REQ_URL =
            String.format("/api/v1/targets/%s/recordingOptions", SELF_REFERENCE_TARGET_ID);

    @Test
    void testSaveEmptyRecordingDoesNotArchiveRecordingFile() throws Exception {
        try {

            CompletableFuture<JsonObject> optionsResponse = new CompletableFuture<>();
            MultiMap optionsForm = MultiMap.caseInsensitiveMultiMap();
            optionsForm.add("toDisk", "false");
            optionsForm.add("maxSize", "0");

            webClient
                    .patch(OPTIONS_REQ_URL)
                    .sendForm(
                            optionsForm,
                            ar -> {
                                if (assertRequestStatus(ar, optionsResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    optionsResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            optionsResponse.get();

            // Create an empty recording
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");

            webClient
                    .post(RECORDING_REQ_URL)
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    postResponse.complete(null);
                                }
                            });

            postResponse.get();

            // Attempt to save the recording to archive
            CompletableFuture<String> saveResponse = new CompletableFuture<>();
            webClient
                    .patch(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveResponse)) {
                                    saveResponse.complete(ar.result().bodyAsString());
                                }
                            });

            MatcherAssert.assertThat(saveResponse.get(), Matchers.equalTo(null));

            // Assert that no recording was archived
            CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
            webClient
                    .get(ARCHIVED_REQ_URL)
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture1)) {
                                    listRespFuture1.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray listResp = listRespFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertTrue(listResp.isEmpty());

        } finally {

            // Clean up recording
            CompletableFuture<JsonObject> deleteActiveRecResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteActiveRecResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteActiveRecResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            MatcherAssert.assertThat(deleteActiveRecResponse.get(), Matchers.equalTo(null));

            // Reset default target recording options
            CompletableFuture<JsonObject> optionsResponse = new CompletableFuture<>();
            MultiMap optionsForm = MultiMap.caseInsensitiveMultiMap();
            optionsForm.add("toDisk", "unset");
            optionsForm.add("maxSize", "unset");

            webClient
                    .patch(OPTIONS_REQ_URL)
                    .sendForm(
                            optionsForm,
                            ar -> {
                                if (assertRequestStatus(ar, optionsResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    optionsResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            optionsResponse.get();
        }
    }
}
