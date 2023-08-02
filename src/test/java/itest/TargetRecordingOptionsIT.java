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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TargetRecordingOptionsIT extends StandardSelfTest {

    static final String OPTIONS_REQ_URL =
            String.format("/api/v1/targets/%s/recordingOptions", SELF_REFERENCE_TARGET_ID);
    static final String OPTIONS_LIST_REQ_URL =
            String.format("/api/v2/targets/%s/recordingOptionsList", SELF_REFERENCE_TARGET_ID);
    static final String RECORDING_NAME = "test_recording";
    static final String ARCHIVED_REQ_URL = "/api/v1/recordings";

    @AfterAll
    static void resetDefaultRecordingOptions() throws Exception {
        CompletableFuture<JsonObject> dumpResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("maxAge", "unset");
        form.add("toDisk", "unset");
        form.add("maxSize", "unset");

        webClient
                .patch(OPTIONS_REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, dumpResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                dumpResponse.complete(null);
                            }
                        });

        try {
            dumpResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ITestCleanupFailedException("Failed to reset default recording options", e);
        }
    }

    @Test
    @Order(1)
    public void testGetTargetRecordingOptionsListReturnsListOfRecordingOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_LIST_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                Map.of(
                                        "result",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        "Name",
                                                        "description",
                                                        "Recording name",
                                                        "defaultValue",
                                                        "Recording"),
                                                Map.of(
                                                        "name",
                                                        "Duration",
                                                        "description",
                                                        "Duration of recording",
                                                        "defaultValue",
                                                        "30s[s]"),
                                                Map.of(
                                                        "name",
                                                        "Max Size",
                                                        "description",
                                                        "Maximum size of recording",
                                                        "defaultValue",
                                                        "0B[B]"),
                                                Map.of(
                                                        "name",
                                                        "Max Age",
                                                        "description",
                                                        "Maximum age of the events in the"
                                                                + " recording",
                                                        "defaultValue",
                                                        "0s[s]"),
                                                Map.of(
                                                        "name",
                                                        "To disk",
                                                        "description",
                                                        "Record to disk",
                                                        "defaultValue",
                                                        "false"),
                                                Map.of(
                                                        "name",
                                                        "Dump on Exit",
                                                        "description",
                                                        "Dump recording data to disk on JVM exit",
                                                        "defaultValue",
                                                        "false")))));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(2)
    public void testGetTargetRecordingOptionsReturnsDefaultOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        // FIXME the default disk option returned is different than the actual value used in
        // recordings
        // https://github.com/cryostatio/cryostat/issues/263
        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 0, "toDisk", false, "maxSize", 0));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(3)
    public void testPostRecordingSetsDiskOptionToTrue() throws Exception {
        try {
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            CompletableFuture<Integer> maxAge = new CompletableFuture<>();
            CompletableFuture<Boolean> toDisk = new CompletableFuture<>();
            CompletableFuture<Integer> maxSize = new CompletableFuture<>();

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    postResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            maxAge.complete(postResponse.get().getInteger("maxAge"));
            toDisk.complete(postResponse.get().getBoolean("toDisk"));
            maxSize.complete(postResponse.get().getInteger("maxSize"));

            MatcherAssert.assertThat(maxAge.get(), Matchers.equalTo(0));
            MatcherAssert.assertThat(toDisk.get(), Matchers.equalTo(true));
            MatcherAssert.assertThat(maxSize.get(), Matchers.equalTo(0));

        } finally {
            // Delete the recording
            CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRespFuture)) {
                                    deleteRespFuture.complete(null);
                                }
                            });

            try {
                deleteRespFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", RECORDING_NAME), e);
            }
        }
    }

    @Test
    @Order(4)
    public void testPatchTargetRecordingOptionsUpdatesOptions() throws Exception {

        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("maxAge", "60");
        form.add("toDisk", "true");
        form.add("maxSize", "1000");

        webClient
                .patch(OPTIONS_REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 60, "toDisk", true, "maxSize", 1000));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(5)
    public void testGetTargetRecordingOptionsReturnsUpdatedOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 60, "toDisk", true, "maxSize", 1000));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(6)
    public void testPostRecordingSetsArchiveOnStop() throws Exception {
        String recordingName = "ArchiveOnStop";
        String archivedgName = "";
        try {
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", recordingName);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add("archiveOnStop", "true");

            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    postResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            JsonObject response1 = postResponse.get();
            MatcherAssert.assertThat(response1.getString("name"), Matchers.equalTo(recordingName));

            Thread.sleep(8_000L);
            // Assert that the recording was archived
            CompletableFuture<JsonArray> listRespFuture1 = new CompletableFuture<>();
            webClient
                    .get(ARCHIVED_REQ_URL)
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, listRespFuture1)) {
                                    listRespFuture1.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            JsonObject response2 = listRespFuture1.get().getJsonObject(0);
            MatcherAssert.assertThat(
                    response2.getString("name").contains(recordingName), Matchers.is(true));
            archivedgName = response2.getString("name");

        } finally {
            // Delete the recording
            CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, recordingName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRespFuture)) {
                                    deleteRespFuture.complete(null);
                                }
                            });

            try {
                deleteRespFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", recordingName), e);
            }

            // Delete the archive
            CompletableFuture<Void> deleteArchiveRespFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/beta/recordings/%s/%s",
                                    SELF_REFERENCE_TARGET_ID, archivedgName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteArchiveRespFuture)) {
                                    deleteArchiveRespFuture.complete(null);
                                }
                            });

            try {
                deleteArchiveRespFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format(
                                "Failed to delete target archive recording %s", archivedgName),
                        e);
            }
        }
    }
}
