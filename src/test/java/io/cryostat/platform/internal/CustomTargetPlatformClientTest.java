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
