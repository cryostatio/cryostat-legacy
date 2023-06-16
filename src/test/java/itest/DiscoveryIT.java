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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import itest.bases.ExternalTargetsTest;
import itest.util.Podman;
import itest.util.http.V2Response;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DiscoveryIT extends ExternalTargetsTest {

    private static final Gson gson =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    static final int NUM_EXT_CONTAINERS = 1;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            Podman.ImageSpec spec =
                    new Podman.ImageSpec(
                            "vertx-fib-demo-" + i,
                            FIB_DEMO_IMAGESPEC,
                            Map.of("JMX_PORT", String.valueOf(9093 + i)));
            specs.add(spec);
            CONTAINERS.add(Podman.runAppWithAgent(10_000 + i, spec));
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
    static void cleanup() throws Exception {
        for (String id : CONTAINERS) {
            Podman.stop(id);
        }
    }

    @Test
    void testDiscovery() throws Exception {
        CompletableFuture<V2Response<DiscoveryResult>> resp = new CompletableFuture<>();
        webClient
                .get("/api/v2.1/discovery")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                new TypeToken<
                                                        V2Response<
                                                                DiscoveryResult>>() {}.getType()));
                            }
                        });
        V2Response<DiscoveryResult> actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(actual.meta.status, Matchers.equalTo("OK"));

        // root node should be a Universe type
        Node rootNode = actual.data.result;
        MatcherAssert.assertThat(
                rootNode, Matchers.hasProperty("name", Matchers.equalTo("Universe")));
        MatcherAssert.assertThat(rootNode, Matchers.hasProperty("target", Matchers.nullValue()));
        MatcherAssert.assertThat(
                rootNode, Matchers.hasProperty("children", Matchers.not(Matchers.empty())));

        // with two child Realms: the Agent instance and Custom Targets
        List<Node> realms = rootNode.children;
        MatcherAssert.assertThat(
                realms,
                Matchers.everyItem(Matchers.hasProperty("nodeType", Matchers.equalTo("Realm"))));
        MatcherAssert.assertThat(
                realms,
                Matchers.allOf(
                        Matchers.hasItem(
                                Matchers.hasProperty("name", Matchers.equalTo("Custom Targets"))),
                        Matchers.hasItem(
                                Matchers.hasProperty(
                                        "name", Matchers.equalTo("vertx-fib-demo-0")))));
        MatcherAssert.assertThat(realms, Matchers.hasSize(2));
        Node agent =
                realms.stream()
                        .filter(node -> "vertx-fib-demo-0".equals(node.name))
                        .findFirst()
                        .get();
        Node customTargets =
                realms.stream()
                        .filter(node -> "Custom Targets".equals(node.name))
                        .findFirst()
                        .get();

        // Custom Targets should have no children or labels, and should not be a target
        // TODO define a custom target and ensure it appears in this part of the response
        MatcherAssert.assertThat(customTargets.children, Matchers.empty());
        MatcherAssert.assertThat(customTargets.labels.keySet(), Matchers.equalTo(Set.of("REALM")));
        MatcherAssert.assertThat(customTargets.target, Matchers.nullValue());

        // Agent should have no labels and should not be a target, but it should have children
        MatcherAssert.assertThat(agent.labels.keySet(), Matchers.equalTo(Set.of("REALM")));
        MatcherAssert.assertThat(agent.target, Matchers.nullValue());
        MatcherAssert.assertThat(
                agent.children,
                Matchers.everyItem(Matchers.hasProperty("nodeType", Matchers.equalTo("JVM"))));

        // There should be 1 Agent target JVM
        List<Node> jvms = agent.children;
        MatcherAssert.assertThat(
                jvms, Matchers.everyItem(Matchers.hasProperty("target", Matchers.notNullValue())));
        MatcherAssert.assertThat(
                jvms, Matchers.everyItem(Matchers.hasProperty("children", Matchers.nullValue())));
        MatcherAssert.assertThat(
                jvms,
                Matchers.hasItems(
                        Matchers.allOf(
                                Matchers.hasProperty(
                                        "name",
                                        Matchers.equalTo(
                                                "service:jmx:rmi:///jndi/rmi://cryostat-itests:9093/jmxrmi")),
                                Matchers.hasProperty(
                                        "target",
                                        Matchers.hasProperty(
                                                "connectUrl",
                                                Matchers.equalTo(
                                                        "service:jmx:rmi:///jndi/rmi://cryostat-itests:9093/jmxrmi"))),
                                Matchers.hasProperty(
                                        "target",
                                        Matchers.hasProperty(
                                                "alias", Matchers.equalTo("vertx-fib-demo-0"))))));
        MatcherAssert.assertThat(jvms, Matchers.hasSize(1));
    }

    // public getters in the classes below are needed for Hamcrest Matchers.hasProperty calls
    public static class DiscoveryResult {
        Node result;

        public Node getNode() {
            return result;
        }
    }

    public static class Node {
        String name;
        String nodeType;
        List<Node> children;
        Map<String, String> labels;
        Target target;

        public String getName() {
            return name;
        }

        public String getNodeType() {
            return nodeType;
        }

        public List<Node> getChildren() {
            return children;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public Target getTarget() {
            return target;
        }
    }

    public static class Target {
        String connectUrl;
        String alias;
        Annotations annotations;
        Map<String, String> labels;

        public String getConnectUrl() {
            return connectUrl;
        }

        public String getAlias() {
            return alias;
        }

        public Annotations getAnnotations() {
            return annotations;
        }

        public Map<String, String> getLabels() {
            return labels;
        }
    }

    public static class Annotations {
        Map<String, String> platform;
        Map<String, String> cryostat;

        public Map<String, String> getPlatform() {
            return platform;
        }

        public Map<String, String> getCryostat() {
            return cryostat;
        }
    }
}
