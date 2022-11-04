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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.sys.Environment;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;
import io.cryostat.util.URIUtil;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@EnableKubernetesMockClient(https = false, crud = true)
class KubeApiPlatformClientTest {

    static final String NAMESPACE = "foo-namespace";

    KubeApiPlatformClient platformClient;
    KubernetesClient k8sClient;
    KubernetesMockServer server;
    @Mock JFRConnectionToolkit connectionToolkit;
    @Mock Environment env;
    @Mock Logger logger;

    @BeforeEach
    void setup() throws Exception {
        this.platformClient =
                new KubeApiPlatformClient(NAMESPACE, k8sClient, () -> connectionToolkit, logger);
    }

    @Test
    void shouldReturnEmptyListIfNoEndpointsFound() throws Exception {
        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();
        MatcherAssert.assertThat(result, Matchers.equalTo(Collections.emptyList()));
    }

    @Test
    void shouldReturnListOfMatchingEndpointRefs() throws Exception {
        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.2")
                                        .withHostname("targetA")
                                        .withNewTargetRef()
                                        .withName("targetA")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-80")
                                        .withPort(80)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.3")
                                        .withHostname("targetB")
                                        .withNewTargetRef()
                                        .withName("targetB")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(1234)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.4")
                                        .withHostname("targetC")
                                        .withNewTargetRef()
                                        .withName("targetC")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(9091)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.5")
                                        .withHostname("targetD")
                                        .withNewTargetRef()
                                        .withName("targetD")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(9091)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.6")
                                        .withHostname("targetE")
                                        .withNewTargetRef()
                                        .withName("targetE")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(5678)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();

        k8sClient.endpoints().inNamespace(NAMESPACE).create(endpoints);

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();

        // targetA is intentionally not a matching service
        ServiceRef serv1 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.3", 1234)),
                        "targetB");
        serv1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.3",
                        AnnotationKey.PORT,
                        "1234",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetB"));
        ServiceRef serv2 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.4", 9091)),
                        "targetC");
        serv2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.4",
                        AnnotationKey.PORT,
                        "9091",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetC"));
        ServiceRef serv3 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.5", 9091)),
                        "targetD");
        serv3.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.5",
                        AnnotationKey.PORT,
                        "9091",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetD"));
        ServiceRef serv4 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.6", 5678)),
                        "targetE");
        serv4.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.6",
                        AnnotationKey.PORT,
                        "5678",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetE"));
        MatcherAssert.assertThat(
                result, Matchers.equalTo(Arrays.asList(serv1, serv2, serv3, serv4)));
    }

    @Test
    void shouldReturnDiscoveryTree() throws Exception {
        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.2")
                                        .withHostname("targetA")
                                        .withNewTargetRef()
                                        .withName("targetA")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(9091)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("127.0.0.3")
                                        .withHostname("targetB")
                                        .withNewTargetRef()
                                        .withName("targetB")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(1234)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).create(endpoints);

        platformClient.start();
        EnvironmentNode realmNode = platformClient.getDiscoveryTree();
        ServiceRef serv1 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.2", 9091)),
                        "targetA");
        serv1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.2",
                        AnnotationKey.PORT,
                        "9091",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetA"));
        ServiceRef serv2 =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("127.0.0.3", 1234)),
                        "targetB");
        serv2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "127.0.0.3",
                        AnnotationKey.PORT,
                        "1234",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "targetB"));

        MatcherAssert.assertThat(realmNode.getName(), Matchers.equalTo("KubernetesApi"));
        MatcherAssert.assertThat(realmNode.getNodeType(), Matchers.equalTo(BaseNodeType.REALM));
        MatcherAssert.assertThat(realmNode.getLabels().size(), Matchers.equalTo(0));
        MatcherAssert.assertThat(realmNode.getChildren(), Matchers.hasSize(1));

        AbstractNode realmChild = realmNode.getChildren().get(0);
        MatcherAssert.assertThat(realmChild, Matchers.instanceOf(EnvironmentNode.class));
        EnvironmentNode namespaceNode = (EnvironmentNode) realmChild;
        MatcherAssert.assertThat(namespaceNode.getName(), Matchers.equalTo(NAMESPACE));
        MatcherAssert.assertThat(
                namespaceNode.getNodeType(), Matchers.equalTo(KubernetesNodeType.NAMESPACE));
        MatcherAssert.assertThat(namespaceNode.getLabels().size(), Matchers.equalTo(0));
        MatcherAssert.assertThat(namespaceNode.getChildren(), Matchers.hasSize(2));

        MatcherAssert.assertThat(
                namespaceNode.getChildren(),
                Matchers.everyItem(
                        Matchers.hasProperty(
                                "nodeType", Matchers.equalTo(KubernetesNodeType.POD))));
        MatcherAssert.assertThat(
                namespaceNode.getChildren(),
                Matchers.allOf(
                        Matchers.hasItem(Matchers.hasProperty("name", Matchers.equalTo("targetA"))),
                        Matchers.hasItem(
                                Matchers.hasProperty("name", Matchers.equalTo("targetB")))));

        EnvironmentNode podA =
                (EnvironmentNode)
                        namespaceNode.getChildren().stream()
                                .filter(c -> c.getName().equals("targetA"))
                                .findFirst()
                                .get();
        EnvironmentNode podB =
                (EnvironmentNode)
                        namespaceNode.getChildren().stream()
                                .filter(c -> c.getName().equals("targetB"))
                                .findFirst()
                                .get();

        // FIXME fill in more intermediate nodes, ie. ReplicationController, DeploymentConfig
        Matcher<AbstractNode> sr1Matcher =
                Matchers.allOf(
                        Matchers.hasProperty(
                                "name", Matchers.equalTo(serv1.getServiceUri().toString())),
                        Matchers.hasProperty(
                                "nodeType", Matchers.equalTo(KubernetesNodeType.ENDPOINT)),
                        Matchers.hasProperty("target", Matchers.equalTo(serv1)));
        MatcherAssert.assertThat(podA.getChildren(), Matchers.contains(sr1Matcher));
        Matcher<AbstractNode> sr2Matcher =
                Matchers.allOf(
                        Matchers.hasProperty(
                                "name", Matchers.equalTo(serv2.getServiceUri().toString())),
                        Matchers.hasProperty(
                                "nodeType", Matchers.equalTo(KubernetesNodeType.ENDPOINT)),
                        Matchers.hasProperty("target", Matchers.equalTo(serv2)));
        MatcherAssert.assertThat(podB.getChildren(), Matchers.contains(sr2Matcher));
    }

    @Test
    public void shouldNotifyOnAsyncAdded() throws Exception {
        CompletableFuture<TargetDiscoveryEvent> eventFuture = new CompletableFuture<>();
        platformClient.addTargetDiscoveryListener(eventFuture::complete);

        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        platformClient.start();

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("192.168.1.10")
                                        .withHostname("watchedTarget")
                                        .withNewTargetRef()
                                        .withName("watchedTarget")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(9876)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).create(endpoints);

        TargetDiscoveryEvent evt = eventFuture.get(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat(evt.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        ServiceRef serv =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("192.168.1.10", 9876)),
                        "watchedTarget");
        serv.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "192.168.1.10",
                        AnnotationKey.PORT,
                        "9876",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "watchedTarget"));
        MatcherAssert.assertThat(evt.getServiceRef(), Matchers.equalTo(serv));
    }

    @Test
    public void shouldNotifyOnAsyncDeleted() throws Exception {
        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("192.168.1.10")
                                        .withHostname("watchedTarget")
                                        .withNewTargetRef()
                                        .withName("watchedTarget")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(9876)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();

        CountDownLatch latch = new CountDownLatch(2);
        Queue<TargetDiscoveryEvent> events = new ArrayDeque<>(2);
        platformClient.addTargetDiscoveryListener(
                tde -> {
                    events.add(tde);
                    latch.countDown();
                });

        platformClient.start();

        k8sClient.endpoints().inNamespace(NAMESPACE).create(endpoints);
        k8sClient.endpoints().inNamespace(NAMESPACE).delete(endpoints);

        latch.await();
        Thread.sleep(100); // to ensure no more events are coming

        MatcherAssert.assertThat(events, Matchers.hasSize(2));

        ServiceRef serv =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("192.168.1.10", 9876)),
                        "watchedTarget");
        serv.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "192.168.1.10",
                        AnnotationKey.PORT,
                        "9876",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "watchedTarget"));

        TargetDiscoveryEvent found = events.remove();
        MatcherAssert.assertThat(found.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(found.getServiceRef(), Matchers.equalTo(serv));

        TargetDiscoveryEvent lost = events.remove();
        MatcherAssert.assertThat(lost.getEventKind(), Matchers.equalTo(EventKind.LOST));
        MatcherAssert.assertThat(lost.getServiceRef(), Matchers.equalTo(serv));
    }

    @Test
    public void shouldNotifyOnAsyncModified() throws Exception {
        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        "/jndi/rmi://" + host + ":" + port + "/jmxrmi");
                            }
                        });

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("192.168.1.10")
                                        .withHostname("watchedTarget")
                                        .withNewTargetRef()
                                        .withName("watchedTarget")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(9876)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();

        CountDownLatch latch = new CountDownLatch(3);
        Queue<TargetDiscoveryEvent> events = new ArrayDeque<>(3);
        platformClient.addTargetDiscoveryListener(
                tde -> {
                    events.add(tde);
                    latch.countDown();
                });

        platformClient.start();

        k8sClient.endpoints().inNamespace(NAMESPACE).create(endpoints);

        endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp("192.168.1.10")
                                        .withHostname("modifiedTarget")
                                        .withNewTargetRef()
                                        .withName("modifiedTarget")
                                        .withKind("Pod")
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(9876)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).replace(endpoints);

        latch.await();
        Thread.sleep(100); // to ensure no more events are coming

        MatcherAssert.assertThat(events, Matchers.hasSize(3));

        ServiceRef original =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("192.168.1.10", 9876)),
                        "watchedTarget");
        original.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "192.168.1.10",
                        AnnotationKey.PORT,
                        "9876",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "watchedTarget"));

        ServiceRef modified =
                new ServiceRef(
                        null,
                        URIUtil.convert(connectionToolkit.createServiceURL("192.168.1.10", 9876)),
                        "modifiedTarget");
        modified.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        "192.168.1.10",
                        AnnotationKey.PORT,
                        "9876",
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        "modifiedTarget"));

        TargetDiscoveryEvent found = events.remove();
        MatcherAssert.assertThat(found.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(found.getServiceRef(), Matchers.equalTo(original));

        TargetDiscoveryEvent lost = events.remove();
        MatcherAssert.assertThat(lost.getEventKind(), Matchers.equalTo(EventKind.LOST));
        MatcherAssert.assertThat(lost.getServiceRef(), Matchers.equalTo(original));

        TargetDiscoveryEvent refound = events.remove();
        MatcherAssert.assertThat(refound.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(refound.getServiceRef(), Matchers.equalTo(modified));
    }
}
