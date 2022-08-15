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

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TargetRecordingsClientErrorIT extends StandardSelfTest {

    static final String REQ_URL =
            String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID);
    static final String TEST_RECORDING_NAME = "workflow_itest";

    @AfterAll
    static void verifyNoRecordingsCreated() throws Exception {
        CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
        webClient
                .get(REQ_URL)
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
    public void testPostRecordingThrowsOnNullForm() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post(REQ_URL)
                .sendForm(
                        MultiMap.caseInsensitiveMultiMap(),
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsWithoutRecordingNameAttribute() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("events", "template=ALL");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsWithoutEventsAttribute() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsOnEmptyRecordingName() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", "");
        form.add("events", "template=ALL");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsOnEmptyEvents() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("events", "");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsOnInvalidIntegerArugment() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("events", "template=ALL");
        form.add("duration", "notAnInt");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPostRecordingThrowsOnInvalidDiskOption() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("events", "template=ALL");
        form.add("toDisk", "notABool");

        webClient
                .post(REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testDeleteRecordingThrowsOnNonExistentRecording() throws Exception {

        CompletableFuture<Void> response = new CompletableFuture<>();
        webClient
                .delete(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    public void testGetRecordingThrowsOnNonExistentRecording() throws Exception {

        CompletableFuture<Void> response = new CompletableFuture<>();
        webClient
                .get(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    public void testPatchRecordingOptionsThrowsOnInvalidIntegerArugment() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("maxAge", "notAnInt");

        webClient
                .patch(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPatchRecordingOptionsThrowsOnInvalidDiskOption() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("toDisk", "notABool");

        webClient
                .patch(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPatchRecordingThrowsOnRecordingNotFound() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .patch(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .sendBuffer(
                        Buffer.buffer("SAVE"),
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    public void testPatchRecordingThrowsOnInvalidRequestBody() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .patch(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .sendBuffer(
                        Buffer.buffer("INVALID_BODY"),
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testPatchRecordingThrowsOnNullRequestBody() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .patch(String.format("%s/%s", REQ_URL, TEST_RECORDING_NAME))
                .sendBuffer(
                        null,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void testUploadRecordingThrowsOnNonExistentRecording() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post(String.format("%s/%s/upload", REQ_URL, TEST_RECORDING_NAME))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
