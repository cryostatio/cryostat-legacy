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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class RecordingMetadataIT extends ExternalTargetsTest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final Map<String, String> NULL_RESULT = new HashMap<>();
    static final Map<String, String> requestLabels =
            Map.of("KEY", "VALUE", "key.2", "some.value", "key3", "1234");
    static Map<String, String> responseLabels;
    static Map<String, String> updatedLabels;
    static final String RECORDING_NAME = "Test_Recording";

    static final int NUM_EXT_CONTAINERS = 1;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        responseLabels = new ConcurrentHashMap<>(requestLabels);
        responseLabels.put("template.name", "ALL");
        responseLabels.put("template.type", "TARGET");

        updatedLabels = new ConcurrentHashMap<>(responseLabels);
        updatedLabels.put("KEY", "updatedValue");
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
                gson.toJson(new Metadata(requestLabels), new TypeToken<Metadata>() {}.getType()));
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

        // verify in-memory recording created with labels
        CompletableFuture<JsonArray> listRespFuture = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
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
        MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(responseLabels));
    }

    @Test
    @Order(1)
    void testRecordingsQueriedFromDifferentTargetIdsHaveSameLabels() throws Exception {
        String aliasTargetOne =
                URLEncodedUtils.formatSegments("service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi");
        String aliasTargetTwo =
                URLEncodedUtils.formatSegments(
                        "service:jmx:rmi:///jndi/rmi://localhost:9091/jmxrmi");
        String aliasTargetThree =
                URLEncodedUtils.formatSegments(String.format("%s:9091", Podman.POD_NAME));

        // verify in-memory recording from previous test created with labels for self with alias
        // aliasTargetOne
        CompletableFuture<JsonArray> listRespFutureTargetOne = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", aliasTargetOne))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFutureTargetOne)) {
                                listRespFutureTargetOne.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listRespTargetOne =
                listRespFutureTargetOne.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonObject recordingInfoTargetOne = listRespTargetOne.getJsonObject(0);
        Metadata actualMetadataTargetOne =
                gson.fromJson(
                        recordingInfoTargetOne.getValue("metadata").toString(),
                        new TypeToken<Metadata>() {}.getType());

        MatcherAssert.assertThat(
                recordingInfoTargetOne.getString("name"), Matchers.equalTo(RECORDING_NAME));
        MatcherAssert.assertThat(
                actualMetadataTargetOne.getLabels(), Matchers.equalTo(responseLabels));

        // verify in-memory recording created with labels from aliased self aliasTargetTwo
        CompletableFuture<JsonArray> listRespFutureTargetTwo = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", aliasTargetTwo))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFutureTargetTwo)) {
                                listRespFutureTargetTwo.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listRespTargetTwo =
                listRespFutureTargetTwo.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonObject recordingInfoTargetTwo = listRespTargetTwo.getJsonObject(0);
        Metadata actualMetadataTargetTwo =
                gson.fromJson(
                        recordingInfoTargetTwo.getValue("metadata").toString(),
                        new TypeToken<Metadata>() {}.getType());

        MatcherAssert.assertThat(
                recordingInfoTargetTwo.getString("name"), Matchers.equalTo(RECORDING_NAME));
        MatcherAssert.assertThat(
                actualMetadataTargetTwo.getLabels(), Matchers.equalTo(responseLabels));

        // verify in-memory recording created with labels from aliased self aliasTargetThree
        CompletableFuture<JsonArray> listRespFutureTargetThree = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", aliasTargetThree))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFutureTargetThree)) {
                                listRespFutureTargetThree.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listRespTargetThree =
                listRespFutureTargetThree.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonObject recordingInfoTargetThree = listRespTargetThree.getJsonObject(0);
        Metadata actualMetadataTargetThree =
                gson.fromJson(
                        recordingInfoTargetThree.getValue("metadata").toString(),
                        new TypeToken<Metadata>() {}.getType());

        MatcherAssert.assertThat(
                recordingInfoTargetThree.getString("name"), Matchers.equalTo(RECORDING_NAME));
        MatcherAssert.assertThat(
                actualMetadataTargetThree.getLabels(), Matchers.equalTo(responseLabels));
    }

    @Test
    @Order(2)
    void testUpdateTargetRecordingLabels() throws Exception {
        // update the recording labels
        Map<String, Map<String, String>> updatedMetadata = Map.of("labels", updatedLabels);
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        webClient
                .post(
                        String.format(
                                "/api/beta/targets/%s/recordings/%s/metadata/labels",
                                SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
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
                .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
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
    @Order(3)
    void testSaveTargetRecordingCopiesLabelsToArchivedRecording() throws Exception {
        String archivedRecordingName = null;
        try {
            // Save the recording to archives
            CompletableFuture<Void> saveResponse = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
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

            MatcherAssert.assertThat(actualMetadata.getLabels(), Matchers.equalTo(updatedLabels));
        } finally {
            // clean up what we created
            CompletableFuture<Void> deleteTargetRecordingFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteTargetRecordingFuture)) {
                                    deleteTargetRecordingFuture.complete(null);
                                }
                            });

            CompletableFuture<Void> deleteArchiveFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/beta/recordings/%s/%s",
                                    SELF_REFERENCE_TARGET_ID, archivedRecordingName))
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
    @Order(4)
    void testStaleMetadataDeletedAndArchivedMetadataPreservedWhenTargetRestarted()
            throws Exception {
        String targetId =
                URLEncodedUtils.formatSegments(
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME));
        CompletableFuture<String> archivedRecordingName = new CompletableFuture<>();

        try {
            String containerId =
                    Podman.run(
                            new Podman.ImageSpec(FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", "9093")));
            // add a new target
            CONTAINERS.add(containerId);
            waitForDiscovery(NUM_EXT_CONTAINERS); // wait for JDP to discover new container(s)

            // create an in-memory recording
            CompletableFuture<Void> dumpRespFuture = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            form.add(
                    "metadata",
                    gson.toJson(
                            new Metadata(updatedLabels), new TypeToken<Metadata>() {}.getType()));
            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", targetId))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, dumpRespFuture)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    dumpRespFuture.complete(null);
                                }
                            });
            dumpRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // save the recording to archives
            CompletableFuture<Void> saveRespFuture = new CompletableFuture<>();
            webClient
                    .patch(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", targetId, RECORDING_NAME))
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, saveRespFuture)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    saveRespFuture.complete(null);
                                    archivedRecordingName.complete(ar.result().bodyAsString());
                                }
                            });

            saveRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // restart the target
            Podman.kill(containerId);
            CONTAINERS.remove(containerId);
            waitForDiscovery(0);

            containerId =
                    Podman.run(
                            new Podman.ImageSpec(FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", "9093")));
            CONTAINERS.add(containerId);

            waitForDiscovery(NUM_EXT_CONTAINERS);

            // check that a new active recording with the same name has a new set of labels
            CompletableFuture<JsonObject> activeRecordingFuture = new CompletableFuture<>();
            form.remove("metadata");

            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", targetId))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, activeRecordingFuture)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    activeRecordingFuture.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject activeMetadata =
                    activeRecordingFuture
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .getJsonObject("metadata");
            JsonObject expectedMetadata =
                    new JsonObject(
                            Map.of(
                                    "labels",
                                    Map.of("template.name", "ALL", "template.type", "TARGET")));

            MatcherAssert.assertThat(activeMetadata, Matchers.equalTo(expectedMetadata));

            // check archived recording labels still preserved
            CompletableFuture<JsonArray> archivedRecordingsFuture = new CompletableFuture<>();
            webClient
                    .get("/api/v1/recordings")
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

            JsonObject archivedMetadata = recordingInfo.getJsonObject("metadata");
            JsonObject expectedArchivedMetadata = new JsonObject(Map.of("labels", updatedLabels));

            MatcherAssert.assertThat(archivedMetadata, Matchers.equalTo(expectedArchivedMetadata));
        } finally {
            CompletableFuture<Void> deleteTargetRecordingFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s", targetId, RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteTargetRecordingFuture)) {
                                    deleteTargetRecordingFuture.complete(null);
                                }
                            });
            CompletableFuture<Void> deleteArchiveFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/beta/recordings/%s/%s",
                                    targetId,
                                    archivedRecordingName.get(
                                            REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
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
                        String.format("Failed to delete recording %s", RECORDING_NAME), e);
            }

            try {
                deleteArchiveFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new ITestCleanupFailedException(
                        String.format(
                                "Failed to delete archived recording %s",
                                archivedRecordingName.get(
                                        REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)),
                        e);
            }
        }
    }
}
