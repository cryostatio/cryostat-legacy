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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
class GraphQLIT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    private final ExecutorService worker = ForkJoinPool.commonPool();

    static final int NUM_EXT_CONTAINERS = 8;
    static final List<String> CONTAINERS = new ArrayList<>();

    static final String TEST_RECORDING_NAME = "archivedRecording";

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", String.valueOf(9093 + i))));
        }
        for (Podman.ImageSpec spec : specs) {
            CONTAINERS.add(Podman.run(spec));
        }
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(NUM_EXT_CONTAINERS);
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
    void testEnvironmentNodeListing() throws Exception {
        CompletableFuture<EnvironmentNodesResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { environmentNodes(filter: { name: \"JDP\" }) { name nodeType"
                        + " descendantTargets { name nodeType } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                EnvironmentNodesResponse.class));
                            }
                        });
        EnvironmentNodesResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        EnvironmentNodes expected = new EnvironmentNodes();

        EnvironmentNode jdp = new EnvironmentNode();
        jdp.name = "JDP";
        jdp.nodeType = "Realm";

        jdp.descendantTargets = new ArrayList<>();
        Node cryostat = new Node();
        cryostat.name = "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi";
        cryostat.nodeType = "JVM";
        jdp.descendantTargets.add(cryostat);

        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            Node target = new Node();
            int port = 9093 + i;
            target.name = "service:jmx:rmi:///jndi/rmi://cryostat-itests:" + port + "/jmxrmi";
            target.nodeType = "JVM";
            jdp.descendantTargets.add(target);
        }

        expected.environmentNodes = List.of(jdp);

        MatcherAssert.assertThat(actual.data, Matchers.equalTo(expected));
    }

    @Test
    @Order(1)
    void testOtherContainersFound() throws Exception {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes { name nodeType labels target { alias serviceUri annotations {"
                        + " cryostat platform } } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                TargetNodesQueryResponse.class));
                            }
                        });
        TargetNodesQueryResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(NUM_EXT_CONTAINERS + 1));

        TargetNode cryostat = new TargetNode();
        Target cryostatTarget = new Target();
        cryostatTarget.alias = "io.cryostat.Cryostat";
        cryostatTarget.serviceUri =
                String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME);
        cryostat.name = cryostatTarget.serviceUri;
        cryostat.target = cryostatTarget;
        cryostat.nodeType = "JVM";
        Annotations cryostatAnnotations = new Annotations();
        cryostatAnnotations.cryostat =
                Map.of(
                        "REALM",
                        "JDP",
                        "JAVA_MAIN",
                        "io.cryostat.Cryostat",
                        "HOST",
                        Podman.POD_NAME,
                        "PORT",
                        "9091");
        cryostatAnnotations.platform = Map.of();
        cryostatTarget.annotations = cryostatAnnotations;
        cryostat.labels = Map.of();
        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasItem(cryostat));

        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            int port = 9093 + i;
            String uri =
                    String.format(
                            "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", Podman.POD_NAME, port);
            String mainClass = "es.andrewazor.demo.Main";
            TargetNode ext = new TargetNode();
            Target target = new Target();
            target.alias = mainClass;
            target.serviceUri = uri;
            ext.name = target.serviceUri;
            ext.target = target;
            ext.nodeType = "JVM";
            Annotations annotations = new Annotations();
            annotations.cryostat =
                    Map.of(
                            "REALM",
                            "JDP",
                            "JAVA_MAIN",
                            mainClass,
                            "HOST",
                            Podman.POD_NAME,
                            "PORT",
                            Integer.toString(port));
            annotations.platform = Map.of();
            target.annotations = annotations;
            ext.labels = Map.of();
            MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasItem(ext));
        }
    }

    @Test
    @Order(2)
    void testQueryForSpecificTargetWithSpecificFields() throws Exception {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) { name nodeType }"
                        + " }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                TargetNodesQueryResponse.class));
                            }
                        });
        TargetNodesQueryResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        String uri =
                String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", Podman.POD_NAME, 9093);
        TargetNode ext = new TargetNode();
        ext.name = uri;
        ext.nodeType = "JVM";
        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasItem(ext));
    }

    @Test
    @Order(3)
    void testStartRecordingMutationOnSpecificTarget() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) {"
                    + " doStartRecording(recording: { name: \"graphql-itest\", duration: 30,"
                    + " template: \"Profiling\", templateType: \"TARGET\", archiveOnStop: true,"
                    + " metadata: { labels: [ { key: \"newLabel\", value: \"someValue\"} ] }  }) {"
                    + " name state duration archiveOnStop }} }");
        Map<String, String> expectedLabels =
                Map.of(
                        "template.name",
                        "Profiling",
                        "template.type",
                        "TARGET",
                        "newLabel",
                        "someValue");
        Future<JsonObject> f =
                worker.submit(
                        () -> {
                            try {
                                return expectNotification(
                                                "ActiveRecordingCreated", 15, TimeUnit.SECONDS)
                                        .get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread.sleep(5000); // Sleep to setup notification listening before query resolves

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                StartRecordingMutationResponse.class));
                                latch.countDown();
                            }
                        });

        latch.await(30, TimeUnit.SECONDS);

        // Ensure ActiveRecordingCreated notification emitted matches expected values
        JsonObject notification = f.get(5, TimeUnit.SECONDS);

        JsonObject notificationRecording =
                notification.getJsonObject("message").getJsonObject("recording");
        MatcherAssert.assertThat(
                notificationRecording.getString("name"), Matchers.equalTo("graphql-itest"));
        MatcherAssert.assertThat(
                notificationRecording.getString("archiveOnStop"), Matchers.equalTo("true"));
        MatcherAssert.assertThat(
                notification.getJsonObject("message").getString("target"),
                Matchers.equalTo(
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
                                Podman.POD_NAME, 9093)));
        Map<String, Object> notificationLabels =
                notificationRecording.getJsonObject("metadata").getJsonObject("labels").getMap();
        for (var entry : expectedLabels.entrySet()) {
            MatcherAssert.assertThat(
                    notificationLabels, Matchers.hasEntry(entry.getKey(), entry.getValue()));
        }

        StartRecordingMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        RecordingNodes nodes = new RecordingNodes();

        ActiveRecording recording = new ActiveRecording();
        recording.name = "graphql-itest";
        recording.duration = 30_000L;
        recording.state = "RUNNING";
        recording.archiveOnStop = true;
        recording.metadata = RecordingMetadata.of(expectedLabels);

        StartRecording startRecording = new StartRecording();
        startRecording.doStartRecording = recording;

        nodes.targetNodes = List.of(startRecording);

        MatcherAssert.assertThat(actual.data, Matchers.equalTo(nodes));
    }

    @Test
    @Order(4)
    void testArchiveMutation() throws Exception {
        Thread.sleep(5000);
        CompletableFuture<ArchiveMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) { recordings {"
                        + " active { data { name doArchive { name } } } } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                ArchiveMutationResponse.class));
                            }
                        });
        ArchiveMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        TargetNode node = actual.data.targetNodes.get(0);

        MatcherAssert.assertThat(node.recordings.active.data, Matchers.hasSize(1));

        ActiveRecording activeRecording = node.recordings.active.data.get(0);

        MatcherAssert.assertThat(activeRecording.name, Matchers.equalTo("graphql-itest"));

        ArchivedRecording archivedRecording = activeRecording.doArchive;
        MatcherAssert.assertThat(
                archivedRecording.name,
                Matchers.matchesRegex(
                        "^es-andrewazor-demo-Main_graphql-itest_[0-9]{8}T[0-9]{6}Z\\.jfr$"));
    }

    @Test
    @Order(5)
    void testActiveRecordingMetadataMutation() throws Exception {
        CompletableFuture<ActiveMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) {"
                        + "recordings { active {"
                        + " data {"
                        + " doPutMetadata(metadata: { labels: ["
                        + " {key:\"template.name\",value:\"Profiling\"},"
                        + " {key:\"template.type\",value:\"TARGET\"},"
                        + " {key:\"newLabel\",value:\"newValue\"}] })"
                        + " { metadata { labels } } } } } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                ActiveMutationResponse.class));
                            }
                        });
        ActiveMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        TargetNode node = actual.data.targetNodes.get(0);

        MatcherAssert.assertThat(node.recordings.active.data, Matchers.hasSize(1));

        ActiveRecording activeRecording = node.recordings.active.data.get(0);

        MatcherAssert.assertThat(
                activeRecording.metadata,
                Matchers.equalTo(
                        RecordingMetadata.of(
                                Map.of(
                                        "template.name",
                                        "Profiling",
                                        "template.type",
                                        "TARGET",
                                        "newLabel",
                                        "newValue"))));
    }

    @Test
    @Order(6)
    void testArchivedRecordingMetadataMutation() throws Exception {
        CompletableFuture<ArchiveMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) {"
                        + "recordings { archived {"
                        + " data { name size "
                        + " doPutMetadata(metadata: { labels: ["
                        + " {key:\"template.name\",value:\"Profiling\"},"
                        + " {key:\"template.type\",value:\"TARGET\"},"
                        + " {key:\"newArchivedLabel\",value:\"newArchivedValue\"}] })"
                        + " { metadata { labels } } } } } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                ArchiveMutationResponse.class));
                            }
                        });
        ArchiveMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        TargetNode node = actual.data.targetNodes.get(0);

        MatcherAssert.assertThat(node.recordings.archived.data, Matchers.hasSize(1));

        ArchivedRecording archivedRecording = node.recordings.archived.data.get(0);
        MatcherAssert.assertThat(archivedRecording.size, Matchers.greaterThan(0L));

        MatcherAssert.assertThat(
                archivedRecording.metadata,
                Matchers.equalTo(
                        RecordingMetadata.of(
                                Map.of(
                                        "template.name",
                                        "Profiling",
                                        "template.type",
                                        "TARGET",
                                        "newArchivedLabel",
                                        "newArchivedValue"))));
    }

    @Test
    @Order(7)
    void testDeleteMutation() throws Exception {
        CompletableFuture<DeleteMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) { recordings {"
                        + " active { data { name doDelete { name }"
                        + " } aggregate { count } }"
                        + " archived { data { name doDelete { name }"
                        + " } aggregate { count size } }"
                        + " } } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                DeleteMutationResponse.class));
                            }
                        });
        DeleteMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        TargetNode node = actual.data.targetNodes.get(0);

        MatcherAssert.assertThat(node.recordings.active.data, Matchers.hasSize(1));
        MatcherAssert.assertThat(node.recordings.archived.data, Matchers.hasSize(1));
        MatcherAssert.assertThat(node.recordings.archived.aggregate.count, Matchers.equalTo(1L));
        MatcherAssert.assertThat(node.recordings.archived.aggregate.size, Matchers.greaterThan(0L));

        ActiveRecording activeRecording = node.recordings.active.data.get(0);
        ArchivedRecording archivedRecording = node.recordings.archived.data.get(0);

        MatcherAssert.assertThat(activeRecording.name, Matchers.equalTo("graphql-itest"));
        MatcherAssert.assertThat(activeRecording.doDelete.name, Matchers.equalTo("graphql-itest"));

        MatcherAssert.assertThat(
                archivedRecording.name,
                Matchers.matchesRegex(
                        "^es-andrewazor-demo-Main_graphql-itest_[0-9]{8}T[0-9]{6}Z\\.jfr$"));
    }

    @Test
    @Order(8)
    void testNodesHaveIds() throws Exception {
        CompletableFuture<EnvironmentNodesResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { environmentNodes(filter: { name: \"JDP\" }) { id descendantTargets { id }"
                        + " } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                EnvironmentNodesResponse.class));
                            }
                        });
        // if any of the nodes in the query did not have an ID property then the request
        // would fail
        EnvironmentNodesResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Set<Integer> observedIds = new HashSet<>();
        for (var env : actual.data.environmentNodes) {
            // ids should be unique
            MatcherAssert.assertThat(observedIds, Matchers.not(Matchers.contains(env.id)));
            observedIds.add(env.id);
            for (var target : env.descendantTargets) {
                MatcherAssert.assertThat(observedIds, Matchers.not(Matchers.contains(target.id)));
                observedIds.add(target.id);
            }
        }
    }

    @Test
    @Order(9)
    void testQueryForSpecificTargetsByNames() throws Exception {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();

        String query =
                String.format(
                        "query { targetNodes(filter: { names:"
                            + " [\"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\","
                            + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9093/jmxrmi\"] }) {"
                            + " name nodeType } }");
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        new JsonObject().put("query", query),
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                TargetNodesQueryResponse.class));
                            }
                        });
        TargetNodesQueryResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<TargetNode> targetNodes = actual.data.targetNodes;

        int expectedSize = 2;

        assertThat(targetNodes.size(), is(expectedSize));

        TargetNode target1 = new TargetNode();
        target1.name = "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi";
        target1.nodeType = "JVM";
        TargetNode target2 = new TargetNode();
        target2.name = "service:jmx:rmi:///jndi/rmi://cryostat-itests:9093/jmxrmi";
        target2.nodeType = "JVM";

        assertThat(targetNodes, hasItem(target1));
        assertThat(targetNodes, hasItem(target2));
    }

    @Test
    @Order(10)
    public void testQueryForFilteredActiveRecordingsByNames() throws Exception {
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

        // Create two new recordings
        CompletableFuture<Void> createRecordingFuture1 = new CompletableFuture<>();
        MultiMap form1 = MultiMap.caseInsensitiveMultiMap();
        form1.add("recordingName", "Recording1");
        form1.add("duration", "5");
        form1.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form1,
                        ar -> {
                            if (assertRequestStatus(ar, createRecordingFuture1)) {
                                createRecordingFuture1.complete(null);
                            }
                        });
        createRecordingFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<Void> createRecordingFuture2 = new CompletableFuture<>();
        MultiMap form2 = MultiMap.caseInsensitiveMultiMap();
        form2.add("recordingName", "Recording2");
        form2.add("duration", "5");
        form2.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form2,
                        ar -> {
                            if (assertRequestStatus(ar, createRecordingFuture2)) {
                                createRecordingFuture2.complete(null);
                            }
                        });
        createRecordingFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // GraphQL Query to filter Active recordings by names
        CompletableFuture<TargetNodesQueryResponse> resp2 = new CompletableFuture<>();
        String query =
                "query { targetNodes (filter: {name:"
                    + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\"}){ recordings"
                    + " {active(filter: { names: [\"Recording1\", \"Recording2\",\"Recording3\"] })"
                    + " {data {name}}}}}";
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        new JsonObject().put("query", query),
                        ar -> {
                            if (assertRequestStatus(ar, resp2)) {
                                resp2.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                TargetNodesQueryResponse.class));
                            }
                        });

        TargetNodesQueryResponse graphqlResp = resp2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<String> filterNames = Arrays.asList("Recording1", "Recording2");

        List<ActiveRecording> filteredRecordings =
                graphqlResp.data.targetNodes.stream()
                        .flatMap(targetNode -> targetNode.recordings.active.data.stream())
                        .filter(recording -> filterNames.contains(recording.name))
                        .collect(Collectors.toList());

        MatcherAssert.assertThat(filteredRecordings.size(), Matchers.equalTo(2));
        ActiveRecording r1 = new ActiveRecording();
        r1.name = "Recording1";
        ActiveRecording r2 = new ActiveRecording();
        r2.name = "Recording2";

        assertThat(filteredRecordings, hasItem(r1));
        assertThat(filteredRecordings, hasItem(r2));

        // Delete recordings
        for (ActiveRecording recording : filteredRecordings) {
            String recordingName = recording.name;
            CompletableFuture<Void> deleteRecordingFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, recordingName))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRecordingFuture)) {
                                    deleteRecordingFuture.complete(null);
                                }
                            });
            deleteRecordingFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        // Verify no recordings available
        CompletableFuture<JsonArray> listRespFuture4 = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, listRespFuture4)) {
                                listRespFuture4.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        listResp = listRespFuture4.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(
                "list should have size 0 after deleting recordings",
                listResp.size(),
                Matchers.equalTo(0));
    }

    @Test
    @Order(11)
    public void shouldReturnArchivedRecordingsFilteredByNames() throws Exception {
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

        // Create a new recording
        CompletableFuture<Void> createRecordingFuture = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("duration", "5");
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, createRecordingFuture)) {
                                createRecordingFuture.complete(null);
                            }
                        });
        createRecordingFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Archive the recording
        CompletableFuture<Void> archiveRecordingFuture = new CompletableFuture<>();
        webClient
                .patch(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s",
                                SELF_REFERENCE_TARGET_ID, TEST_RECORDING_NAME))
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                .sendBuffer(
                        Buffer.buffer("SAVE"),
                        ar -> {
                            if (assertRequestStatus(ar, archiveRecordingFuture)) {
                                archiveRecordingFuture.complete(null);
                            } else {

                                archiveRecordingFuture.completeExceptionally(
                                        new RuntimeException("Archive request failed"));
                            }
                        });

        archiveRecordingFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // retrieve to match the exact name
        CompletableFuture<JsonArray> archivedRecordingsFuture2 = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/recordings"))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, archivedRecordingsFuture2)) {
                                archivedRecordingsFuture2.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray retrivedArchivedRecordings =
                archivedRecordingsFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonObject retrievedArchivedrecordings = retrivedArchivedRecordings.getJsonObject(0);
        String retrievedArchivedRecordingsName = retrievedArchivedrecordings.getString("name");

        // GraphQL Query to filter Archived recordings by names
        CompletableFuture<TargetNodesQueryResponse> resp2 = new CompletableFuture<>();

        String query =
                "query { targetNodes {"
                        + "recordings {"
                        + "archived(filter: { names: [\""
                        + retrievedArchivedRecordingsName
                        + "\",\"someOtherName\"] }) {"
                        + "data {"
                        + "name"
                        + "}"
                        + "}"
                        + "}"
                        + "}"
                        + "}";
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        new JsonObject().put("query", query),
                        ar -> {
                            if (assertRequestStatus(ar, resp2)) {
                                resp2.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                TargetNodesQueryResponse.class));
                            }
                        });

        TargetNodesQueryResponse graphqlResp = resp2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<ArchivedRecording> archivedRecordings2 =
                graphqlResp.data.targetNodes.stream()
                        .flatMap(targetNode -> targetNode.recordings.archived.data.stream())
                        .collect(Collectors.toList());

        int filteredRecordingsCount = archivedRecordings2.size();
        Assertions.assertEquals(
                1, filteredRecordingsCount, "Number of filtered recordings should be 1");

        ArchivedRecording archivedRecording = archivedRecordings2.get(0);
        String filteredName = archivedRecording.name;
        Assertions.assertEquals(
                filteredName,
                retrievedArchivedRecordingsName,
                "Filtered name should match the archived recording name");

        // Delete archived recording by name
        for (ArchivedRecording archrecording : archivedRecordings2) {
            String nameMatch = archrecording.name;

            CompletableFuture<Void> deleteFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/beta/recordings/%s/%s",
                                    SELF_REFERENCE_TARGET_ID, nameMatch))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteFuture)) {
                                    deleteFuture.complete(null);
                                } else {
                                    deleteFuture.completeExceptionally(
                                            new RuntimeException("Delete request failed"));
                                }
                            });

            deleteFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Retrieve the list of updated archived recordings to verify that the targeted
        // recordings
        // have been deleted
        CompletableFuture<JsonArray> updatedArchivedRecordingsFuture = new CompletableFuture<>();
        webClient
                .get("/api/v1/recordings")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, updatedArchivedRecordingsFuture)) {
                                updatedArchivedRecordingsFuture.complete(
                                        ar.result().bodyAsJsonArray());
                            }
                        });

        JsonArray updatedArchivedRecordings =
                updatedArchivedRecordingsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the targeted recordings have been deleted
        boolean recordingsDeleted =
                updatedArchivedRecordings.stream()
                        .noneMatch(
                                json -> {
                                    JsonObject recording = (JsonObject) json;
                                    return recording.getString("name").equals(TEST_RECORDING_NAME);
                                });

        Assertions.assertTrue(
                recordingsDeleted, "The targeted archived recordings should be deleted");

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

        deleteRespFuture1.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<JsonArray> savedRecordingsFuture = new CompletableFuture<>();
        webClient
                .get("/api/v1/recordings")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, savedRecordingsFuture)) {
                                savedRecordingsFuture.complete(ar.result().bodyAsJsonArray());
                            }
                        });

        JsonArray savedRecordings =
                savedRecordingsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (Object savedRecording : savedRecordings) {
            String recordingName = ((JsonObject) savedRecording).getString("name");
            if (recordingName.matches("archivedRecordings")) {
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

                deleteRespFuture2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Order(12)
    public void testQueryforFilteredEnvironmentNodesByNames() throws Exception {
        CompletableFuture<EnvironmentNodesResponse> resp = new CompletableFuture<>();

        String query =
                "query { environmentNodes(filter: { names: [\"anotherName1\","
                        + " \"JDP\",\"anotherName2\"] }) { name nodeType } }";
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        new JsonObject().put("query", query),
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                EnvironmentNodesResponse.class));
                            }
                        });

        EnvironmentNodesResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<EnvironmentNode> environmentNodes = actual.data.environmentNodes;

        Assertions.assertEquals(1, environmentNodes.size(), "The list filtered should be 1");

        boolean nameExists = false;
        for (EnvironmentNode environmentNode : environmentNodes) {
            if (environmentNode.name.matches("JDP")) {
                nameExists = true;
                break;
            }
        }
        Assertions.assertTrue(nameExists, "Name not found");
    }

    @Test
    @Order(13)
    void testReplaceAlwaysOnStoppedRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Stop the Recording
            notificationRecording = stopRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("STOPPED", notificationRecording.getString("state"));

            // Restart the recording with replace:ALWAYS
            notificationRecording = restartRecording(ReplacementPolicy.ALWAYS);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(14)
    void testReplaceNeverOnStoppedRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Stop the Recording
            notificationRecording = stopRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("STOPPED", notificationRecording.getString("state"));

            // Restart the recording with replace:NEVER
            JsonObject error = restartRecordingWithError(ReplacementPolicy.NEVER);
            Assertions.assertTrue(
                    error.getString("message")
                            .contains("Recording with name \"test\" already exists"),
                    "Expected error message to contain 'Recording with name \"test\" already"
                            + " exists'");
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(15)
    void testReplaceStoppedOnStoppedRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Stop the Recording
            notificationRecording = stopRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("STOPPED", notificationRecording.getString("state"));

            // Restart the recording with replace:STOPPED
            notificationRecording = restartRecording(ReplacementPolicy.STOPPED);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"STOPPED", "NEVER"})
    @Order(16)
    void testReplaceStoppedOrNeverOnRunningRecording(String replace) throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Restart the recording with replace:NEVER
            JsonObject error = restartRecordingWithError(ReplacementPolicy.STOPPED);
            Assertions.assertTrue(
                    error.getString("message")
                            .contains("Recording with name \"test\" already exists"),
                    "Expected error message to contain 'Recording with name \"test\" already"
                            + " exists'");
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(17)
    void testReplaceAlwaysOnRunningRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Restart the recording with replace:ALWAYS
            notificationRecording = restartRecording(ReplacementPolicy.ALWAYS);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(18)
    void testRestartTrueOnRunningRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Restart the recording with replace:ALWAYS
            notificationRecording = restartRecording(true);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(19)
    void testRestartFalseOnRunningRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Restart the recording with replace:NEVER
            JsonObject error = restartRecordingWithError(false);
            Assertions.assertTrue(
                    error.getString("message")
                            .contains("Recording with name \"test\" already exists"),
                    "Expected error message to contain 'Recording with name \"test\" already"
                            + " exists'");
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(20)
    void testRestartTrueOnStoppedRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Stop the Recording
            notificationRecording = stopRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("STOPPED", notificationRecording.getString("state"));

            // Restart the recording with replace:ALWAYS
            notificationRecording = restartRecording(true);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @Test
    @Order(21)
    void testRestartFalseOnStoppedRecording() throws Exception {
        try {
            // Start a Recording
            JsonObject notificationRecording = startRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));

            // Stop the Recording
            notificationRecording = stopRecording();
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("STOPPED", notificationRecording.getString("state"));

            // Restart the recording with replace:NEVER
            JsonObject error = restartRecordingWithError(false);
            Assertions.assertTrue(
                    error.getString("message")
                            .contains("Recording with name \"test\" already exists"),
                    "Expected error message to contain 'Recording with name \"test\" already"
                            + " exists'");
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALWAYS", "STOPPED", "NEVER"})
    @Order(22)
    void testStartRecordingwithReplaceNever(String replace) throws Exception {
        try {
            JsonObject notificationRecording =
                    restartRecording(ReplacementPolicy.fromString(replace));
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the Recording
            deleteRecording();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Order(23)
    void testRestartRecordingWithReplaceTrue(boolean restart) throws Exception {
        try {
            JsonObject notificationRecording = restartRecording(restart);
            Assertions.assertEquals("test", notificationRecording.getString("name"));
            Assertions.assertEquals("RUNNING", notificationRecording.getString("state"));
        } finally {
            // Delete the recording
            deleteRecording();
        }
    }

    static class Target {
        String alias;
        String serviceUri;
        Annotations annotations;

        @Override
        public int hashCode() {
            return Objects.hash(alias, serviceUri, annotations);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Target other = (Target) obj;
            return Objects.equals(alias, other.alias)
                    && Objects.equals(serviceUri, other.serviceUri)
                    && Objects.equals(annotations, other.annotations);
        }
    }

    static class Annotations {
        Map<String, String> platform;
        Map<String, String> cryostat;

        @Override
        public int hashCode() {
            return Objects.hash(cryostat, platform);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Annotations other = (Annotations) obj;
            return Objects.equals(cryostat, other.cryostat)
                    && Objects.equals(platform, other.platform);
        }
    }

    static class ArchivedRecording {
        String name;
        String reportUrl;
        String downloadUrl;
        RecordingMetadata metadata;
        long size;
        long archivedTime;

        ArchivedRecording doDelete;

        @Override
        public String toString() {
            return "ArchivedRecording [doDelete="
                    + doDelete
                    + ", downloadUrl="
                    + downloadUrl
                    + ", metadata="
                    + metadata
                    + ", name="
                    + name
                    + ", reportUrl="
                    + reportUrl
                    + ", size="
                    + size
                    + ", archivedTime="
                    + archivedTime
                    + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(doDelete, downloadUrl, metadata, name, reportUrl, size);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ArchivedRecording other = (ArchivedRecording) obj;
            return Objects.equals(doDelete, other.doDelete)
                    && Objects.equals(downloadUrl, other.downloadUrl)
                    && Objects.equals(metadata, other.metadata)
                    && Objects.equals(name, other.name)
                    && Objects.equals(reportUrl, other.reportUrl)
                    && Objects.equals(size, other.size);
        }
    }

    static class AggregateInfo {
        long count;
        long size;

        @Override
        public String toString() {
            return "AggregateInfo [count=" + count + ", size=" + size + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(count, size);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            AggregateInfo other = (AggregateInfo) obj;
            if (count != other.count) return false;
            if (size != other.size) return false;
            return true;
        }
    }

    static class Archived {
        List<ArchivedRecording> data;
        AggregateInfo aggregate;

        @Override
        public String toString() {
            return "Archived [data=" + data + ", aggregate=" + aggregate + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, aggregate);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Archived other = (Archived) obj;
            return Objects.equals(data, other.data) && Objects.equals(aggregate, other.aggregate);
        }
    }

    static class Recordings {
        Active active;
        Archived archived;

        @Override
        public String toString() {
            return "Recordings [active=" + active + ", archived=" + archived + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(active, archived);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Recordings other = (Recordings) obj;
            return Objects.equals(active, other.active) && Objects.equals(archived, other.archived);
        }
    }

    static class TargetNode {
        String name;
        String nodeType;
        Map<String, String> labels;
        Target target;
        Recordings recordings;
        ActiveRecording doStartRecording;

        @Override
        public String toString() {
            return "TargetNode [doStartRecording="
                    + doStartRecording
                    + ", labels="
                    + labels
                    + ", name="
                    + name
                    + ", nodeType="
                    + nodeType
                    + ", recordings="
                    + recordings
                    + ", target="
                    + target
                    + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(doStartRecording, labels, name, nodeType, recordings, target);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TargetNode other = (TargetNode) obj;
            return Objects.equals(doStartRecording, other.doStartRecording)
                    && Objects.equals(labels, other.labels)
                    && Objects.equals(name, other.name)
                    && Objects.equals(nodeType, other.nodeType)
                    && Objects.equals(recordings, other.recordings)
                    && Objects.equals(target, other.target);
        }
    }

    static class TargetNodes {

        List<TargetNode> targetNodes;

        @Override
        public int hashCode() {
            return Objects.hash(targetNodes);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TargetNodes other = (TargetNodes) obj;
            return Objects.equals(targetNodes, other.targetNodes);
        }

        @Override
        public String toString() {
            return "TargetNodes [targetNodes=" + targetNodes + "]";
        }
    }

    static class TargetNodesQueryResponse {
        TargetNodes data;

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TargetNodesQueryResponse other = (TargetNodesQueryResponse) obj;
            return Objects.equals(data, other.data);
        }
    }

    static class Active {
        List<ActiveRecording> data;
        AggregateInfo aggregate;

        @Override
        public String toString() {
            return "Active [data=" + data + ", aggregate=" + aggregate + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, aggregate);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Active other = (Active) obj;
            return Objects.equals(data, other.data) && Objects.equals(aggregate, other.aggregate);
        }
    }

    static class ActiveRecording {
        String name;
        String reportUrl;
        String downloadUrl;
        RecordingMetadata metadata;
        String state;
        long startTime;
        long duration;
        boolean continuous;
        boolean toDisk;
        long maxSize;
        long maxAge;
        boolean archiveOnStop;

        ArchivedRecording doArchive;
        ActiveRecording doDelete;

        @Override
        public int hashCode() {
            return Objects.hash(
                    continuous,
                    doArchive,
                    doDelete,
                    downloadUrl,
                    duration,
                    maxAge,
                    maxSize,
                    archiveOnStop,
                    metadata,
                    name,
                    reportUrl,
                    startTime,
                    state,
                    toDisk);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ActiveRecording other = (ActiveRecording) obj;
            return continuous == other.continuous
                    && Objects.equals(doArchive, other.doArchive)
                    && Objects.equals(doDelete, other.doDelete)
                    && Objects.equals(downloadUrl, other.downloadUrl)
                    && duration == other.duration
                    && maxAge == other.maxAge
                    && maxSize == other.maxSize
                    && archiveOnStop == other.archiveOnStop
                    && Objects.equals(metadata, other.metadata)
                    && Objects.equals(name, other.name)
                    && Objects.equals(reportUrl, other.reportUrl)
                    && startTime == other.startTime
                    && Objects.equals(state, other.state)
                    && toDisk == other.toDisk;
        }

        @Override
        public String toString() {
            return "ActiveRecording [continuous="
                    + continuous
                    + ", doArchive="
                    + doArchive
                    + ", doDelete="
                    + doDelete
                    + ", downloadUrl="
                    + downloadUrl
                    + ", duration="
                    + duration
                    + ", maxAge="
                    + maxAge
                    + ", maxSize="
                    + maxSize
                    + ", archiveOnStop="
                    + archiveOnStop
                    + ", metadata="
                    + metadata
                    + ", name="
                    + name
                    + ", reportUrl="
                    + reportUrl
                    + ", startTime="
                    + startTime
                    + ", state="
                    + state
                    + ", toDisk="
                    + toDisk
                    + "]";
        }
    }

    static class RecordingMetadata {
        Map<String, String> labels;

        public static RecordingMetadata of(Map<String, String> of) {
            return null;
        }

        @Override
        public int hashCode() {
            return Objects.hash(labels);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RecordingMetadata other = (RecordingMetadata) obj;
            return Objects.equals(labels, other.labels);
        }

        @Override
        public String toString() {
            return "RecordingMetadata [labels=" + labels + "]";
        }
    }

    static class StartRecording {
        ActiveRecording doStartRecording;
        ArchivedRecording doArchive;
        ActiveRecording doPutMetadata;

        @Override
        public int hashCode() {
            return Objects.hash(doArchive, doStartRecording, doPutMetadata);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StartRecording other = (StartRecording) obj;
            return Objects.equals(doArchive, other.doArchive)
                    && Objects.equals(doStartRecording, other.doStartRecording)
                    && Objects.equals(doPutMetadata, other.doPutMetadata);
        }

        @Override
        public String toString() {
            return "StartRecording [doArchive="
                    + doArchive
                    + ", doStartRecording="
                    + doStartRecording
                    + ", doPutMetadata="
                    + doPutMetadata
                    + "]";
        }
    }

    static class RecordingNodes {
        List<StartRecording> targetNodes;

        @Override
        public int hashCode() {
            return Objects.hash(targetNodes);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RecordingNodes other = (RecordingNodes) obj;
            return Objects.equals(targetNodes, other.targetNodes);
        }

        @Override
        public String toString() {
            return "RecordingNodes [targetNodes=" + targetNodes + "]";
        }
    }

    static class StartRecordingMutationResponse {
        RecordingNodes data;

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StartRecordingMutationResponse other = (StartRecordingMutationResponse) obj;
            return Objects.equals(data, other.data);
        }

        @Override
        public String toString() {
            return "StartRecordingMutationResponse [data=" + data + "]";
        }
    }

    static class Node {
        int id;
        String name;
        String nodeType;

        @Override
        public String toString() {
            return "Node [id=" + id + ", name=" + name + ", nodeType=" + nodeType + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, nodeType);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Node other = (Node) obj;
            return id == other.id
                    && Objects.equals(name, other.name)
                    && Objects.equals(nodeType, other.nodeType);
        }
    }

    static class EnvironmentNode extends Node {
        List<Node> descendantTargets;

        @Override
        public String toString() {
            return "EnvironmentNode [descendantTargets="
                    + descendantTargets
                    + ", name="
                    + name
                    + ", nodeType="
                    + nodeType
                    + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(descendantTargets);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            EnvironmentNode other = (EnvironmentNode) obj;
            return Objects.equals(descendantTargets, other.descendantTargets);
        }
    }

    static class EnvironmentNodes {
        List<EnvironmentNode> environmentNodes;

        @Override
        public String toString() {
            return "EnvironmentNodes [environmentNodes=" + environmentNodes + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(environmentNodes);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            EnvironmentNodes other = (EnvironmentNodes) obj;
            return Objects.equals(environmentNodes, other.environmentNodes);
        }
    }

    static class EnvironmentNodesResponse {
        EnvironmentNodes data;

        @Override
        public String toString() {
            return "EnvironmentNodesResponse [data=" + data + "]";
        }

        public EnvironmentNodes getData() {
            return data;
        }

        public void setData(EnvironmentNodes data) {
            this.data = data;
        }
    }

    static class ArchiveMutationResponse {
        TargetNodes data;

        @Override
        public String toString() {
            return "ArchiveMutationResponse [data=" + data + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ArchiveMutationResponse other = (ArchiveMutationResponse) obj;
            return Objects.equals(data, other.data);
        }
    }

    static class ActiveMutationResponse extends ArchiveMutationResponse {
        @Override
        public String toString() {
            return "ActiveMutationResponse [data=" + data + "]";
        }
    }

    static class DeleteMutationResponse {
        TargetNodes data;

        @Override
        public String toString() {
            return "DeleteMutationResponse [data=" + data + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DeleteMutationResponse other = (DeleteMutationResponse) obj;
            return Objects.equals(data, other.data);
        }
    }

    // start recording
    private JsonObject startRecording() throws Exception {
        JsonObject query = new JsonObject();
        CountDownLatch latch = new CountDownLatch(2);

        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        query.put(
                "query",
                "query { targetNodes(filter: {"
                        + " name:\"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\" }) {"
                        + " doStartRecording(recording: { name: \"test\", template:\"Profiling\","
                        + " templateType: \"TARGET\"}) { name state}} }");
        Future<JsonObject> f =
                worker.submit(
                        () -> {
                            try {
                                return expectNotification(
                                                "ActiveRecordingCreated", 15, TimeUnit.SECONDS)
                                        .get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread.sleep(5000);

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                StartRecordingMutationResponse.class));
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        JsonObject notification = f.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return notification.getJsonObject("message").getJsonObject("recording");
    }

    // Stop the Recording
    private JsonObject stopRecording() throws Exception {
        JsonObject query = new JsonObject();
        CountDownLatch latch = new CountDownLatch(2);

        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        query.put(
                "query",
                "query { targetNodes(filter: { name:"
                        + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\" })  {"
                        + " recordings { active { data { doStop { name state } } } } } }");

        Future<JsonObject> f2 =
                worker.submit(
                        () -> {
                            try {
                                return expectNotification(
                                                "ActiveRecordingStopped", 15, TimeUnit.SECONDS)
                                        .get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread.sleep(5000);

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                StartRecordingMutationResponse.class));
                                latch.countDown();
                            }
                        });

        latch.await(30, TimeUnit.SECONDS);

        JsonObject notification = f2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return notification.getJsonObject("message").getJsonObject("recording");
    }

    // Delete the Recording
    private void deleteRecording() throws Exception {
        JsonObject query = new JsonObject();
        CountDownLatch latch = new CountDownLatch(1);

        CompletableFuture<JsonObject> resp = new CompletableFuture<>();
        query.put(
                "query",
                "query { targetNodes(filter: { name:"
                        + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\" }) {"
                        + " recordings { active { data { doDelete { name state } } } } } }");

        Thread.sleep(5000);

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(), JsonObject.class));
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // Restart the recording with replace:ALWAYS
    private JsonObject restartRecording(ReplacementPolicy replace) throws Exception {
        JsonObject query = new JsonObject();
        CountDownLatch latch = new CountDownLatch(2);

        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        query.put(
                "query",
                String.format(
                        "query { targetNodes(filter: { name:"
                            + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\" }) {"
                            + " doStartRecording(recording: { name: \"test\","
                            + " template:\"Profiling\", templateType: \"TARGET\", replace: %s}) {"
                            + " name state }} }",
                        replace.name()));
        Future<JsonObject> f =
                worker.submit(
                        () -> {
                            try {
                                return expectNotification(
                                                "ActiveRecordingCreated", 15, TimeUnit.SECONDS)
                                        .get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread.sleep(5000);

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                StartRecordingMutationResponse.class));
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        JsonObject notification = f.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return notification.getJsonObject("message").getJsonObject("recording");
    }

    private JsonObject restartRecording(boolean restart) throws Exception {
        JsonObject query = new JsonObject();
        CountDownLatch latch = new CountDownLatch(2);

        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        query.put(
                "query",
                String.format(
                        "query { targetNodes(filter: { name:"
                            + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\" }) {"
                            + " doStartRecording(recording: { name: \"test\","
                            + " template:\"Profiling\", templateType: \"TARGET\", restart: %b}) {"
                            + " name state }} }",
                        restart));
        Future<JsonObject> f =
                worker.submit(
                        () -> {
                            try {
                                return expectNotification(
                                                "ActiveRecordingCreated", 15, TimeUnit.SECONDS)
                                        .get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                latch.countDown();
                            }
                        });

        Thread.sleep(5000);

        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                StartRecordingMutationResponse.class));
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        JsonObject notification = f.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return notification.getJsonObject("message").getJsonObject("recording");
    }

    private JsonObject restartRecordingWithError(ReplacementPolicy replace) throws Exception {
        CompletableFuture<JsonObject> resp = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        JsonObject query = new JsonObject();

        query.put(
                "query",
                String.format(
                        "query { targetNodes(filter: { name:"
                                + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\""
                                + " }) { doStartRecording(recording: { name: \"test\","
                                + " template:\"Profiling\", templateType: \"TARGET\", replace: %s})"
                                + " { name state }} }",
                        replace.name()));
        Thread.sleep(5000);
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(ar.result().bodyAsJsonObject());
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        JsonObject response = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonArray errors = response.getJsonArray("errors");
        return errors.getJsonObject(0);
    }

    private JsonObject restartRecordingWithError(boolean restart) throws Exception {
        // Restart the recording with replace:STOPPED
        CompletableFuture<JsonObject> resp = new CompletableFuture<>();
        CountDownLatch latch = new CountDownLatch(1);
        JsonObject query = new JsonObject();

        query.put(
                "query",
                String.format(
                        "query { targetNodes(filter: { name:"
                                + " \"service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi\""
                                + " }) { doStartRecording(recording: { name: \"test\","
                                + " template:\"Profiling\", templateType: \"TARGET\", restart: %b})"
                                + " { name state }} }",
                        restart));
        Thread.sleep(5000);
        webClient
                .post("/api/v2.2/graphql")
                .sendJson(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(ar.result().bodyAsJsonObject());
                                latch.countDown();
                            }
                        });

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        JsonObject response = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        JsonArray errors = response.getJsonArray("errors");
        return errors.getJsonObject(0);
    }
}
