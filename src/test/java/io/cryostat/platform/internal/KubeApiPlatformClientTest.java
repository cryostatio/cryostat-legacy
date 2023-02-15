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

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
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
import org.mockito.junit.jupiter.MockitoExtension;

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
                new KubeApiPlatformClient(
                        List.of(NAMESPACE), k8sClient, () -> connectionToolkit, logger);
    }

    @Test
    void shouldReturnEmptyListIfNoEndpointsFound() throws Exception {
        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();
        MatcherAssert.assertThat(result, Matchers.equalTo(Collections.emptyList()));
    }

    @Test
    void shouldReturnListOfMatchingEndpointRefs() throws Exception {
        Pod targetA =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetA")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        String ipA = "127.0.0.2";
        String transformedIpA = ipA.replaceAll("\\.", "-");
        int portA = 80;
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetA).create();

        Pod targetB =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetB")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        String ipB = "127.0.0.3";
        String transformedIpB = ipB.replaceAll("\\.", "-");
        int portB = 1234;
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetB).create();

        Pod targetC =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetC")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        String ipC = "127.0.0.4";
        String transformedIpC = ipC.replaceAll("\\.", "-");
        int portC = 9091;
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetC).create();

        Pod targetD =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetD")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        String ipD = "127.0.0.5";
        String transformedIpD = ipD.replaceAll("\\.", "-");
        int portD = 9091;
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetD).create();

        Pod targetE =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetE")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        String ipE = "127.0.0.6";
        String transformedIpE = ipE.replaceAll("\\.", "-");
        int portE = 5678;
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetE).create();

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipA)
                                        .withHostname(targetA.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetA.getMetadata().getName())
                                        .withKind(targetA.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-80")
                                        .withPort(portA)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipB)
                                        .withHostname(targetB.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetB.getMetadata().getName())
                                        .withKind(targetB.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(portB)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipC)
                                        .withHostname(targetC.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetC.getMetadata().getName())
                                        .withKind(targetC.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(portC)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipD)
                                        .withHostname(targetD.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetD.getMetadata().getName())
                                        .withKind(targetD.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(portD)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipE)
                                        .withHostname(targetE.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetE.getMetadata().getName())
                                        .withKind(targetE.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(portE)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();

        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

        platformClient.start();
        List<ServiceRef> result = platformClient.listDiscoverableServices();

        // targetA is intentionally not a matching service
        ServiceRef serv1 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpB, NAMESPACE, portB)),
                        targetB.getMetadata().getName());
        serv1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipB,
                        AnnotationKey.PORT,
                        Integer.toString(portB),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetB.getMetadata().getName()));
        ServiceRef serv2 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpC, NAMESPACE, portC)),
                        targetC.getMetadata().getName());
        serv2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipC,
                        AnnotationKey.PORT,
                        Integer.toString(portC),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetC.getMetadata().getName()));
        ServiceRef serv3 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpD, NAMESPACE, portD)),
                        targetD.getMetadata().getName());
        serv3.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipD,
                        AnnotationKey.PORT,
                        Integer.toString(portD),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetD.getMetadata().getName()));
        ServiceRef serv4 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpE, NAMESPACE, portE)),
                        targetE.getMetadata().getName());
        serv4.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipE,
                        AnnotationKey.PORT,
                        Integer.toString(portE),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetE.getMetadata().getName()));
        MatcherAssert.assertThat(
                result, Matchers.equalTo(Arrays.asList(serv1, serv2, serv3, serv4)));
    }

    @Test
    void shouldReturnDiscoveryTree() throws Exception {
        Pod targetA =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetA")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetA).create();
        Pod targetB =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("targetB")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(targetB).create();

        String ipA = "127.0.0.2";
        String transformedIpA = ipA.replaceAll("\\.", "-");
        int portA = 9091;
        String ipB = "127.0.0.3";
        String transformedIpB = ipB.replaceAll("\\.", "-");
        int portB = 1234;
        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipA)
                                        .withHostname(targetA.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetA.getMetadata().getName())
                                        .withKind(targetA.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("tcp-9091")
                                        .withPort(portA)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ipB)
                                        .withHostname(targetB.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(targetB.getMetadata().getName())
                                        .withKind(targetB.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(portB)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

        platformClient.start();
        EnvironmentNode realmNode = platformClient.getDiscoveryTree();
        ServiceRef serv1 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpA, NAMESPACE, portA)),
                        targetA.getMetadata().getName());
        serv1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipA,
                        AnnotationKey.PORT,
                        Integer.toString(portA),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetA.getMetadata().getName()));
        ServiceRef serv2 =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIpB, NAMESPACE, portB)),
                        targetB.getMetadata().getName());
        serv2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ipB,
                        AnnotationKey.PORT,
                        Integer.toString(portB),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        targetB.getMetadata().getName()));

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
                        Matchers.hasItem(
                                Matchers.hasProperty(
                                        "name", Matchers.equalTo(targetA.getMetadata().getName()))),
                        Matchers.hasItem(
                                Matchers.hasProperty(
                                        "name",
                                        Matchers.equalTo(targetB.getMetadata().getName())))));

        EnvironmentNode podA =
                (EnvironmentNode)
                        namespaceNode.getChildren().stream()
                                .filter(c -> c.getName().equals(targetA.getMetadata().getName()))
                                .findFirst()
                                .get();
        EnvironmentNode podB =
                (EnvironmentNode)
                        namespaceNode.getChildren().stream()
                                .filter(c -> c.getName().equals(targetB.getMetadata().getName()))
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

        Pod watchedTarget =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("watchedTarget")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(watchedTarget).create();

        platformClient.start();

        String ip = "192.168.1.10";
        String transformedIp = ip.replaceAll("\\.", "-");
        int port = 9876;
        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ip)
                                        .withHostname(watchedTarget.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(watchedTarget.getMetadata().getName())
                                        .withKind(watchedTarget.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(port)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

        TargetDiscoveryEvent evt = eventFuture.get(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat(evt.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        ServiceRef serv =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIp, NAMESPACE, port)),
                        watchedTarget.getMetadata().getName());
        serv.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ip,
                        AnnotationKey.PORT,
                        Integer.toString(port),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        watchedTarget.getMetadata().getName()));
        MatcherAssert.assertThat(evt.getServiceRef(), Matchers.equalTo(serv));
    }

    @Test
    public void shouldNotifyOnAsyncDeleted() throws Exception {
        Pod watchedTarget =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("watchedTarget")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(watchedTarget).create();

        String ip = "192.168.1.10";
        String transformedIp = ip.replaceAll("\\.", "-");
        int port = 9876;
        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ip)
                                        .withHostname(watchedTarget.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(watchedTarget.getMetadata().getName())
                                        .withKind(watchedTarget.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(port)
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

        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();
        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).delete();

        latch.await();
        Thread.sleep(100); // to ensure no more events are coming

        MatcherAssert.assertThat(events, Matchers.hasSize(2));

        ServiceRef serv =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIp, NAMESPACE, port)),
                        "watchedTarget");
        serv.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ip,
                        AnnotationKey.PORT,
                        Integer.toString(port),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        watchedTarget.getMetadata().getName()));

        TargetDiscoveryEvent found = events.remove();
        MatcherAssert.assertThat(found.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(found.getServiceRef(), Matchers.equalTo(serv));

        TargetDiscoveryEvent lost = events.remove();
        MatcherAssert.assertThat(lost.getEventKind(), Matchers.equalTo(EventKind.LOST));
        MatcherAssert.assertThat(lost.getServiceRef(), Matchers.equalTo(serv));
    }

    @Test
    public void shouldNotifyOnAsyncModified() throws Exception {
        Pod watchedTarget =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("watchedTarget")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(watchedTarget).create();
        Pod modifiedTarget =
                new PodBuilder()
                        .withNewMetadata()
                        .withName("modifiedTarget")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .build();
        k8sClient.pods().inNamespace(NAMESPACE).resource(modifiedTarget).create();

        String ip = "192.168.1.10";
        String transformedIp = ip.replaceAll("\\.", "-");
        int port = 9876;

        Endpoints endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ip)
                                        .withHostname(watchedTarget.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(watchedTarget.getMetadata().getName())
                                        .withKind(watchedTarget.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(port)
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

        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).create();

        endpoints =
                new EndpointsBuilder()
                        .withNewMetadata()
                        .withName("endpoints1")
                        .withNamespace(NAMESPACE)
                        .endMetadata()
                        .addNewSubset()
                        .withAddresses(
                                new EndpointAddressBuilder()
                                        .withIp(ip)
                                        .withHostname(modifiedTarget.getMetadata().getName())
                                        .withNewTargetRef()
                                        .withName(modifiedTarget.getMetadata().getName())
                                        .withKind(modifiedTarget.getKind())
                                        .withNamespace(NAMESPACE)
                                        .endTargetRef()
                                        .build())
                        .withPorts(
                                new EndpointPortBuilder()
                                        .withName("jfr-jmx")
                                        .withPort(port)
                                        .withProtocol("tcp")
                                        .build())
                        .endSubset()
                        .build();
        k8sClient.endpoints().inNamespace(NAMESPACE).resource(endpoints).replace();

        latch.await();
        Thread.sleep(100); // to ensure no more events are coming

        MatcherAssert.assertThat(events, Matchers.hasSize(2));

        ServiceRef original =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIp, NAMESPACE, port)),
                        watchedTarget.getMetadata().getName());
        original.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ip,
                        AnnotationKey.PORT,
                        Integer.toString(port),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        watchedTarget.getMetadata().getName()));

        ServiceRef modified =
                new ServiceRef(
                        null,
                        URI.create(
                                String.format(
                                        "service:jmx:rmi:///jndi/rmi://%s.%s.pod:%d/jmxrmi",
                                        transformedIp, NAMESPACE, port)),
                        modifiedTarget.getMetadata().getName());
        modified.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "KubernetesApi",
                        AnnotationKey.HOST,
                        ip,
                        AnnotationKey.PORT,
                        Integer.toString(port),
                        AnnotationKey.NAMESPACE,
                        NAMESPACE,
                        AnnotationKey.POD_NAME,
                        modifiedTarget.getMetadata().getName()));

        TargetDiscoveryEvent foundEvent = events.remove();
        MatcherAssert.assertThat(foundEvent.getEventKind(), Matchers.equalTo(EventKind.FOUND));
        MatcherAssert.assertThat(foundEvent.getServiceRef(), Matchers.equalTo(original));

        TargetDiscoveryEvent modifiedEvent = events.remove();
        MatcherAssert.assertThat(
                modifiedEvent.getEventKind(), Matchers.equalTo(EventKind.MODIFIED));
        MatcherAssert.assertThat(modifiedEvent.getServiceRef(), Matchers.equalTo(modified));
    }
}
