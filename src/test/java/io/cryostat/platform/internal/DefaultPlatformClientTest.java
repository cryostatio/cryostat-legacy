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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.DiscoveredJvmDescriptor;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.JvmDiscoveryEvent;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.util.URIUtil;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPlatformClientTest {

    @Mock Logger logger;
    @Mock JvmDiscoveryClient discoveryClient;
    DefaultPlatformClient client;

    @BeforeEach
    void setup() {
        this.client = new DefaultPlatformClient(logger, discoveryClient);
    }

    @Test
    void testShouldAddListenerAndStartDiscovery() throws Exception {
        verifyNoInteractions(discoveryClient);

        client.start();

        InOrder inOrder = Mockito.inOrder(discoveryClient);
        inOrder.verify(discoveryClient).addListener(client);
        inOrder.verify(discoveryClient).start();
    }

    @Test
    void testDiscoverableServiceMapping() throws Exception {

        DiscoveredJvmDescriptor desc1 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url1 =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi");
        when(desc1.getMainClass()).thenReturn("com.example.Main");
        when(desc1.getJmxServiceUrl()).thenReturn(url1);

        DiscoveredJvmDescriptor desc2 = mock(DiscoveredJvmDescriptor.class);
        when(desc2.getJmxServiceUrl()).thenThrow(MalformedURLException.class);

        DiscoveredJvmDescriptor desc3 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url2 =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi");
        when(desc3.getMainClass()).thenReturn("io.cryostat.Cryostat");
        when(desc3.getJmxServiceUrl()).thenReturn(url2);

        when(discoveryClient.getDiscoveredJvmDescriptors())
                .thenReturn(List.of(desc1, desc2, desc3));

        List<ServiceRef> results = client.listDiscoverableServices();

        ServiceRef exp1 =
                new ServiceRef(
                        null, URIUtil.convert(desc1.getJmxServiceUrl()), desc1.getMainClass());
        exp1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        desc1.getMainClass(),
                        AnnotationKey.HOST,
                        "cryostat",
                        AnnotationKey.PORT,
                        "9091"));
        ServiceRef exp2 =
                new ServiceRef(
                        null, URIUtil.convert(desc3.getJmxServiceUrl()), desc3.getMainClass());
        exp2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        desc3.getMainClass(),
                        AnnotationKey.HOST,
                        "cryostat",
                        AnnotationKey.PORT,
                        "9092"));

        assertThat(results, equalTo(List.of(exp1, exp2)));
    }

    @Test
    void testDiscoveryTree() throws Exception {

        DiscoveredJvmDescriptor desc1 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url1 =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi");
        when(desc1.getMainClass()).thenReturn("com.example.Main");
        when(desc1.getJmxServiceUrl()).thenReturn(url1);

        DiscoveredJvmDescriptor desc2 = mock(DiscoveredJvmDescriptor.class);
        when(desc2.getJmxServiceUrl()).thenThrow(MalformedURLException.class);

        DiscoveredJvmDescriptor desc3 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url2 =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi");
        when(desc3.getMainClass()).thenReturn("io.cryostat.Cryostat");
        when(desc3.getJmxServiceUrl()).thenReturn(url2);

        when(discoveryClient.getDiscoveredJvmDescriptors())
                .thenReturn(List.of(desc1, desc2, desc3));

        EnvironmentNode realmNode = client.getDiscoveryTree();

        ServiceRef exp1 =
                new ServiceRef(
                        null, URIUtil.convert(desc1.getJmxServiceUrl()), desc1.getMainClass());
        exp1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        desc1.getMainClass(),
                        AnnotationKey.HOST,
                        "cryostat",
                        AnnotationKey.PORT,
                        "9091"));
        ServiceRef exp2 =
                new ServiceRef(
                        null, URIUtil.convert(desc3.getJmxServiceUrl()), desc3.getMainClass());
        exp2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        desc3.getMainClass(),
                        AnnotationKey.HOST,
                        "cryostat",
                        AnnotationKey.PORT,
                        "9092"));

        MatcherAssert.assertThat(realmNode.getName(), Matchers.equalTo("JDP"));
        MatcherAssert.assertThat(realmNode.getNodeType(), Matchers.equalTo(BaseNodeType.REALM));
        MatcherAssert.assertThat(realmNode.getLabels().size(), Matchers.equalTo(0));
        MatcherAssert.assertThat(realmNode.getChildren(), Matchers.hasSize(2));

        Matcher<AbstractNode> sr1Matcher =
                Matchers.allOf(
                        Matchers.hasProperty(
                                "name", Matchers.equalTo(exp1.getServiceUri().toString())),
                        Matchers.hasProperty(
                                "nodeType", Matchers.equalTo(DefaultPlatformClient.NODE_TYPE)),
                        Matchers.hasProperty("target", Matchers.equalTo(exp1)));
        Matcher<AbstractNode> sr2Matcher =
                Matchers.allOf(
                        Matchers.hasProperty(
                                "name", Matchers.equalTo(exp2.getServiceUri().toString())),
                        Matchers.hasProperty(
                                "nodeType", Matchers.equalTo(DefaultPlatformClient.NODE_TYPE)),
                        Matchers.hasProperty("target", Matchers.equalTo(exp2)));
        MatcherAssert.assertThat(
                realmNode.getChildren(), Matchers.hasItems(sr1Matcher, sr2Matcher));
    }

    @Test
    void testAcceptDiscoveryEvent() throws Exception {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi");
        String javaMain = "com.example.Main";
        DiscoveredJvmDescriptor desc = mock(DiscoveredJvmDescriptor.class);
        when(desc.getMainClass()).thenReturn(javaMain);
        when(desc.getJmxServiceUrl()).thenReturn(url);
        JvmDiscoveryEvent evt = mock(JvmDiscoveryEvent.class);
        when(evt.getEventKind()).thenReturn(EventKind.FOUND);
        when(evt.getJvmDescriptor()).thenReturn(desc);

        CompletableFuture<TargetDiscoveryEvent> future = new CompletableFuture<>();
        client.addTargetDiscoveryListener(future::complete);

        client.accept(evt);

        verifyNoInteractions(discoveryClient);

        TargetDiscoveryEvent event = future.get(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat(event.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        ServiceRef serviceRef = new ServiceRef(null, URIUtil.convert(url), javaMain);
        serviceRef.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        "com.example.Main",
                        AnnotationKey.HOST,
                        "cryostat",
                        AnnotationKey.PORT,
                        "9091"));
        MatcherAssert.assertThat(event.getServiceRef(), Matchers.equalTo(serviceRef));
    }
}
