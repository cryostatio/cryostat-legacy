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
package io.cryostat.platform.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.util.URIUtil;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomTargetPlatformClientTest {

    CustomTargetPlatformClient client;

    static final ServiceRef SERVICE_REF;

    static {
        try {
            SERVICE_REF =
                    new ServiceRef(
                            null,
                            URIUtil.convert(
                                    new JMXServiceURL(
                                            "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi")),
                            "TestTarget");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Mock DiscoveryStorage storage;

    @BeforeEach
    void setup() {
        this.client = new CustomTargetPlatformClient(() -> storage);
    }

    @Test
    void shouldProduceDiscoveryTree() throws IOException {
        client.addTarget(SERVICE_REF);

        EnvironmentNode realmNode = client.getDiscoveryTree();

        MatcherAssert.assertThat(realmNode.getName(), Matchers.equalTo("Custom Targets"));
        MatcherAssert.assertThat(realmNode.getNodeType(), Matchers.equalTo(BaseNodeType.REALM));
        MatcherAssert.assertThat(realmNode.getLabels().size(), Matchers.equalTo(0));
        MatcherAssert.assertThat(realmNode.getChildren(), Matchers.hasSize(1));

        MatcherAssert.assertThat(
                realmNode.getChildren(),
                Matchers.hasItem(
                        Matchers.allOf(
                                Matchers.hasProperty(
                                        "name",
                                        Matchers.equalTo(SERVICE_REF.getServiceUri().toString())),
                                Matchers.hasProperty(
                                        "nodeType",
                                        Matchers.equalTo(CustomTargetPlatformClient.NODE_TYPE)),
                                Matchers.hasProperty("target", Matchers.equalTo(SERVICE_REF)))));
    }

    @Test
    void testAddNewTarget() throws Exception {
        CompletableFuture<TargetDiscoveryEvent> future = new CompletableFuture<>();
        client.addTargetDiscoveryListener(future::complete);

        Assertions.assertTrue(client.addTarget(SERVICE_REF));
        TargetDiscoveryEvent event = future.get(1, TimeUnit.SECONDS);

        MatcherAssert.assertThat(event.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(event.getServiceRef(), Matchers.equalTo(SERVICE_REF));
    }

    @Test
    void testAddDuplicateTarget() throws Exception {
        Assertions.assertTrue(client.addTarget(SERVICE_REF));
        Assertions.assertFalse(client.addTarget(SERVICE_REF));
    }

    @Test
    void testRemoveTarget() throws Exception {
        List<TargetDiscoveryEvent> list = new ArrayList<>();
        client.addTargetDiscoveryListener(list::add);

        Assertions.assertTrue(client.addTarget(SERVICE_REF));
        Assertions.assertTrue(client.removeTarget(SERVICE_REF));

        MatcherAssert.assertThat(list, Matchers.hasSize(2));
        MatcherAssert.assertThat(list.get(0).getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(list.get(0).getServiceRef(), Matchers.equalTo(SERVICE_REF));
        MatcherAssert.assertThat(list.get(1).getEventKind(), Matchers.equalTo(EventKind.LOST));
        MatcherAssert.assertThat(list.get(1).getServiceRef(), Matchers.equalTo(SERVICE_REF));
    }

    @Test
    void testRemoveNonexistentTarget() throws IOException {
        List<TargetDiscoveryEvent> list = new ArrayList<>();
        client.addTargetDiscoveryListener(list::add);

        Assertions.assertFalse(client.removeTarget(SERVICE_REF));

        MatcherAssert.assertThat(list, Matchers.empty());
    }

    @Test
    void testRemoveTargetByUrl() throws Exception {
        List<TargetDiscoveryEvent> list = new ArrayList<>();
        client.addTargetDiscoveryListener(list::add);

        Assertions.assertTrue(client.addTarget(SERVICE_REF));
        Assertions.assertTrue(client.removeTarget(SERVICE_REF.getServiceUri()));

        MatcherAssert.assertThat(list, Matchers.hasSize(2));
        MatcherAssert.assertThat(list.get(0).getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(list.get(0).getServiceRef(), Matchers.equalTo(SERVICE_REF));
        MatcherAssert.assertThat(list.get(1).getEventKind(), Matchers.equalTo(EventKind.LOST));
        MatcherAssert.assertThat(list.get(1).getServiceRef(), Matchers.equalTo(SERVICE_REF));
    }

    @Test
    void testRemoveNonexistentTargetByUrl() throws IOException {
        List<TargetDiscoveryEvent> list = new ArrayList<>();
        client.addTargetDiscoveryListener(list::add);

        Assertions.assertFalse(client.removeTarget(SERVICE_REF.getServiceUri()));

        MatcherAssert.assertThat(list, Matchers.empty());
    }
}
