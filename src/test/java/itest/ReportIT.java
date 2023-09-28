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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReportIT extends StandardSelfTest {

    static final String TEST_RECORDING_NAME = "someRecording";
    static final String REPORT_REQ_URL =
            String.format("/api/beta/reports/%s", SELF_REFERENCE_TARGET_ID);
    static final String RECORDING_REQ_URL =
            String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID);
    static final String ARCHIVE_REQ_URL =
            String.format("/api/beta/recordings/%s", SELF_REFERENCE_TARGET_ID);

    @Test
    void testGetReportShouldSendFile() throws Exception {

        CompletableFuture<String> saveRecordingResp = new CompletableFuture<>();
        String savedRecordingName = null;

        try {
            // Create a recording
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "1");
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

            postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Make a webserver request to generate some recording data
            CompletableFuture<JsonArray> targetGetResponse = new CompletableFuture<>();
            webClient
                    .get("/api/v1/targets")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, targetGetResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.JSON.mime()));
                                    targetGetResponse.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            targetGetResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Save the recording to archive
            webClient
                    .patch(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveRecordingResp)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    saveRecordingResp.complete(ar.result().bodyAsString());
                                }
                            });

            savedRecordingName = saveRecordingResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Get a report for the above recording
            CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
            webClient
                    .get(String.format("%s/%s", REPORT_REQ_URL, savedRecordingName))
                    .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.JSON.mime())
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.JSON.mime()));
                                    getResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject response = getResponse.get();
            MatcherAssert.assertThat(response, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    response.getMap(), Matchers.is(Matchers.aMapWithSize(Matchers.greaterThan(8))));

            // Get a filtered report for the above recording
            CompletableFuture<JsonObject> getFilteredResponse = new CompletableFuture<>();
            webClient
                    .get(String.format("%s/%s", REPORT_REQ_URL, savedRecordingName))
                    .addQueryParam("filter", "heap")
                    .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.JSON.mime())
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getFilteredResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.JSON.mime()));
                                    getFilteredResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });
            JsonObject filteredResponse = getFilteredResponse.get();
            MatcherAssert.assertThat(filteredResponse, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    filteredResponse.getMap(), Matchers.is(Matchers.aMapWithSize(8)));
            Assertions.assertTrue(filteredResponse.containsKey("HeapContent"));
            Assertions.assertTrue(filteredResponse.containsKey("StringDeduplication"));
            Assertions.assertTrue(filteredResponse.containsKey("PrimitiveToObjectConversion"));
            Assertions.assertTrue(filteredResponse.containsKey("GcFreedRatio"));
            Assertions.assertTrue(filteredResponse.containsKey("HighGc"));
            Assertions.assertTrue(filteredResponse.containsKey("HeapDump"));
            Assertions.assertTrue(filteredResponse.containsKey("Allocations.class"));
            Assertions.assertTrue(filteredResponse.containsKey("LowOnPhysicalMemory"));
            for (var obj : filteredResponse.getMap().entrySet()) {
                var value = JsonObject.mapFrom(obj.getValue());
                Assertions.assertTrue(value.containsKey("score"));
                MatcherAssert.assertThat(
                        value.getDouble("score"),
                        Matchers.anyOf(
                                Matchers.equalTo(-1d),
                                Matchers.equalTo(-2d),
                                Matchers.equalTo(-3d),
                                Matchers.both(Matchers.lessThanOrEqualTo(100d))
                                        .and(Matchers.greaterThanOrEqualTo(0d))));
                Assertions.assertTrue(value.containsKey("name"));
                MatcherAssert.assertThat(
                        value.getString("name"), Matchers.not(Matchers.emptyOrNullString()));
                Assertions.assertTrue(value.containsKey("topic"));
                MatcherAssert.assertThat(
                        value.getString("topic"), Matchers.not(Matchers.emptyOrNullString()));
                Assertions.assertTrue(value.containsKey("evaluation"));
            }

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
            try {
                MatcherAssert.assertThat(
                        deleteActiveRecResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        Matchers.equalTo(null));
            } catch (ExecutionException | InterruptedException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", TEST_RECORDING_NAME),
                        e);
            }

            CompletableFuture<JsonObject> deleteArchivedRecResp = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/%s", ARCHIVE_REQ_URL, savedRecordingName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteArchivedRecResp)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteArchivedRecResp.complete(ar.result().bodyAsJsonObject());
                                }
                            });
            try {
                deleteArchivedRecResp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete archived recording %s", savedRecordingName),
                        e);
            }
        }
    }

    @Test
    void testGetReportThrowsWithNonExistentRecordingName() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .get(String.format("%s/%s", REPORT_REQ_URL, TEST_RECORDING_NAME))
                .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.JSON.mime())
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
