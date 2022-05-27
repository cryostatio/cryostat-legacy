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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

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
                            Map.of("JMX_PORT", String.valueOf(9093 + i), "USE_AUTH", "true")));
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
    @Order(1)
    void testOtherContainersFound() throws Exception {
        CompletableFuture<TargetNodesQueryResponse> resp = new CompletableFuture<>();
        JsonObject query = new JsonObject();
        query.put(
                "query",
                "query { targetNodes { name nodeType labels target { alias serviceUri annotations { cryostat platform } } } }");
        webClient
                .post("/api/beta/graphql")
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
                "query { targetNodes(filter: { annotations: \"PORT == 9093\" }) { name nodeType } }");
        webClient
                .post("/api/beta/graphql")
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

    static class TargetNode {
        String name;
        String nodeType;
        Map<String, String> labels;
        Target target;

        @Override
        public int hashCode() {
            return Objects.hash(labels, name, nodeType, target);
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
            return Objects.equals(labels, other.labels)
                    && Objects.equals(name, other.name)
                    && Objects.equals(nodeType, other.nodeType)
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
}
