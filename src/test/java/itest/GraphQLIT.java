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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;

import com.google.gson.Gson;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class GraphQLIT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final int NUM_EXT_CONTAINERS = 8;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                            Map.of("JMX_PORT", String.valueOf(9093 + i))));
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
        CompletableFuture<StartRecordingMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) {"
                    + " doStartRecording(recording: { name: \"graphql-itest\", duration: 30,"
                    + " template: \"Profiling\", templateType: \"TARGET\"  }) { name state duration"
                    + " }} }");
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
                            }
                        });
        StartRecordingMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        RecordingNodes nodes = new RecordingNodes();

        ActiveRecording recording = new ActiveRecording();
        recording.name = "graphql-itest";
        recording.duration = 30_000L;
        recording.state = "RUNNING";

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
                        + " active { name doArchive { name } } } } }");
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

        MatcherAssert.assertThat(node.recordings.active, Matchers.hasSize(1));

        ActiveRecording activeRecording = node.recordings.active.get(0);

        MatcherAssert.assertThat(activeRecording.name, Matchers.equalTo("graphql-itest"));

        ArchivedRecording archivedRecording = activeRecording.doArchive;
        MatcherAssert.assertThat(
                archivedRecording.name,
                Matchers.matchesRegex(
                        "^es-andrewazor-demo-Main_graphql-itest_[0-9]{8}T[0-9]{6}Z\\.jfr$"));
    }

    @Test
    @Order(5)
    void testDeleteMutation() throws Exception {
        CompletableFuture<DeleteMutationResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) { recordings {"
                    + " active { name doDelete { name } } archived { name doDelete { name } } } }"
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
                                                DeleteMutationResponse.class));
                            }
                        });
        DeleteMutationResponse actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(actual.data.targetNodes, Matchers.hasSize(1));

        TargetNode node = actual.data.targetNodes.get(0);

        MatcherAssert.assertThat(node.recordings.active, Matchers.hasSize(1));
        MatcherAssert.assertThat(node.recordings.archived.data, Matchers.hasSize(1));
        MatcherAssert.assertThat(node.recordings.archived.aggregate.count, Matchers.equalTo(1L));

        ActiveRecording activeRecording = node.recordings.active.get(0);
        ArchivedRecording archivedRecording = node.recordings.archived.data.get(0);

        MatcherAssert.assertThat(activeRecording.name, Matchers.equalTo("graphql-itest"));
        MatcherAssert.assertThat(activeRecording.doDelete.name, Matchers.equalTo("graphql-itest"));

        MatcherAssert.assertThat(
                archivedRecording.name,
                Matchers.matchesRegex(
                        "^es-andrewazor-demo-Main_graphql-itest_[0-9]{8}T[0-9]{6}Z\\.jfr$"));
        MatcherAssert.assertThat(
                archivedRecording.doDelete.name, Matchers.equalTo(archivedRecording.name));
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
                    + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(doDelete, downloadUrl, metadata, name, reportUrl);
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
                    && Objects.equals(reportUrl, other.reportUrl);
        }
    }

    static class AggregateInfo {
        Long count;

        @Override
        public String toString() {
            return "AggregateInfo [count=" + count + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(count);
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
            AggregateInfo other = (AggregateInfo) obj;
            return Objects.equals(count, other.count);
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
        List<ActiveRecording> active;
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

        @Override
        public int hashCode() {
            return Objects.hash(doArchive, doStartRecording);
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
                    && Objects.equals(doStartRecording, other.doStartRecording);
        }

        @Override
        public String toString() {
            return "StartRecording [doArchive="
                    + doArchive
                    + ", doStartRecording="
                    + doStartRecording
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
        String name;
        String nodeType;

        @Override
        public String toString() {
            return "Node [name=" + name + ", nodeType=" + nodeType + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, nodeType);
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
            return Objects.equals(name, other.name) && Objects.equals(nodeType, other.nodeType);
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
}
