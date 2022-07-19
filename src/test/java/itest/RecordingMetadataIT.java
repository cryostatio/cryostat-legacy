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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class RecordingMetadataIT extends ExternalTargetsTest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static Map<String, String> testLabels;
    static Map<String, String> expectedLabels;
    static final String TARGET_ID = "localhost";
    static final String RECORDING_NAME = "Test_Recording";

    static final int NUM_EXT_CONTAINERS = 1;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        testLabels = Map.of("KEY", "VALUE", "key.2", "some.value", "key3", "1234");
        expectedLabels =
                Map.of(
                        "KEY",
                        "VALUE",
                        "key.2",
                        "some.value",
                        "key3",
                        "1234",
                        "template.name",
                        "ALL",
                        "template.type",
                        "TARGET");
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
        }
    }

    @Test
    @Order(0)
    void testStartRecordingWithLabels() throws Exception {
        // create an in-memory recording
        CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", RECORDING_NAME);
        form.add("duration", "5");
        form.add("events", "template=ALL");
        form.add(
                "metadata",
                gson.toJson(new Metadata(expectedLabels), new TypeToken<Metadata>() {}.getType()));
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
        Metadata actualMetadata =
                gson.fromJson(
                        recordingInfo.getValue("metadata").toString(),
                        new TypeToken<Metadata>() {}.getType());

        MatcherAssert.assertThat(recordingInfo.getString("name"), Matchers.equalTo(RECORDING_NAME));
        MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(expectedLabels));
    }

    @Test
    @Order(1)
    void testUpdateTargetRecordingLabels() throws Exception {
        // update the recording labels
        Map<String, String> updatedLabels = expectedLabels;
        updatedLabels.put("KEY", "updatedValue");
        Map<String, Map<String, String>> updatedMetadata = Map.of("labels", updatedLabels);
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        webClient
                .post(
                        String.format(
                                "/api/beta/targets/%s/recordings/%s/metadata/labels",
                                TARGET_ID, RECORDING_NAME))
                .sendBuffer(
                        Buffer.buffer(gson.toJson(updatedLabels, Map.class)),
                        ar -> {
                            if (assertRequestStatus(ar, postResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                postResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", updatedMetadata)));
        MatcherAssert.assertThat(
                postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedResponse));

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
        Metadata actualMetadata =
                gson.fromJson(
                        recordingInfo.getValue("metadata").toString(),
                        new TypeToken<Metadata>() {}.getType());

        MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(updatedLabels));
    }

    @Test
    @Order(2)
    void testSaveTargetRecordingCopiesLabelsToArchivedRecording() throws Exception {
        String archivedRecordingName = null;
        try {
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
            JsonArray archivedRecordings =
                    listRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JsonObject recordingInfo = archivedRecordings.getJsonObject(0);
            archivedRecordingName = recordingInfo.getString("name");

            Metadata actualMetadata =
                    gson.fromJson(
                            recordingInfo.getValue("metadata").toString(),
                            new TypeToken<Metadata>() {}.getType());

            Map<String, String> expected = new HashMap<>(expectedLabels);
            expected.put("KEY", "updatedValue");
            MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(expectedLabels));
        } finally {
            // clean up what we created
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
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", RECORDING_NAME), e);
            }

            try {
                deleteArchiveFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format(
                                "Failed to delete archived recording %s", archivedRecordingName),
                        e);
            }
        }
    }

    @Test
    @Order(3)
    void testActiveRecordingMetadataDeletedWhenTargetKilled() throws Exception {
        String targetId = Podman.POD_NAME + ":9093";
        String archivedRecordingName = null;

        try {

            String containerId =
                    Podman.run(
                            new Podman.ImageSpec(
                                    "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                                    Map.of("JMX_PORT", "9093", "USE_AUTH", "true")));
            // add a new target
            CONTAINERS.add(containerId);
            waitForDiscovery(NUM_EXT_CONTAINERS); // wait for JDP to discover new container(s)

            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=Profiling");
            form.add(
                    "metadata",
                    gson.toJson(new Metadata(testLabels), new TypeToken<Metadata>() {}.getType()));
            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", targetId))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, dumpRespFuture)) {
                                    dumpRespFuture.complete(null);
                                }
                            });
            dumpRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // FIXME no recording data generated
            // generate data
            Thread.sleep(5000);

            // save the recording to archives
            CompletableFuture<Void> saveRespFuture = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", targetId, RECORDING_NAME))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveRespFuture)) {
                                    saveRespFuture.complete(null);
                                }
                            });

            saveRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            CompletableFuture<JsonArray> archivedRecordingsFuture = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/recordings"))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, archivedRecordingsFuture)) {
                                    archivedRecordingsFuture.complete(
                                            ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray archivedRecordings =
                    archivedRecordingsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            JsonObject recordingInfo = archivedRecordings.getJsonObject(0);

            Metadata actualMetadata =
                    gson.fromJson(
                            recordingInfo.getValue("metadata").toString(),
                            new TypeToken<Metadata>() {}.getType());

            MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(expectedLabels));

            // restart the target
            Podman.kill(containerId);
            CONTAINERS.remove(containerId);

            containerId =
                    Podman.run(
                            new Podman.ImageSpec(
                                    "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                                    Map.of("JMX_PORT", "9093", "USE_AUTH", "true")));
            CONTAINERS.add(containerId);

            waitForDiscovery(NUM_EXT_CONTAINERS);

            // check archived recording labels still preserved
            CompletableFuture<JsonArray> archivedRecordingsFuture2 = new CompletableFuture<>();
            webClient
                    .get(String.format("/api/v1/recordings"))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, archivedRecordingsFuture2)) {
                                    archivedRecordingsFuture2.complete(
                                            ar.result().bodyAsJsonArray());
                                }
                            });
            archivedRecordings =
                    archivedRecordingsFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            recordingInfo = archivedRecordings.getJsonObject(0);
            actualMetadata =
                    gson.fromJson(
                            recordingInfo.getValue("metadata").toString(),
                            new TypeToken<Metadata>() {}.getType());

            MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(expectedLabels));

        } finally {
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
                deleteArchiveFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete recording %s", archivedRecordingName), e);
            }
        }
    }
}
