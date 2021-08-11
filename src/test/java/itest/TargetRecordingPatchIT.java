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
import java.util.concurrent.TimeUnit;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
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

            // Create an empty recording
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add("maxSize", "0");

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
            CompletableFuture<JsonObject> saveResponse = new CompletableFuture<>();
            webClient
                    .patch(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(204));
                                    saveResponse.complete(ar.result().bodyAsJsonObject());
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
        }
    }
}
