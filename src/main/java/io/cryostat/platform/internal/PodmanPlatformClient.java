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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.apache.commons.lang3.StringUtils;

public class PodmanPlatformClient extends AbstractPlatformClient {

    public static final String REALM = "Podman";
    public static final String CRYOSTAT_LABEL = "io.cryostat.connectUrl";

    private final Vertx vertx;
    private final WebClient webClient;
    private final Gson gson;
    private final SocketAddress podmanSocket;
    private final Logger logger;
    private long timerId;

    private final CopyOnWriteArrayList<ContainerSpec> containers = new CopyOnWriteArrayList<>();

    PodmanPlatformClient(Vertx vertx, SocketAddress podmanSocket, Gson gson, Logger logger) {
        this.vertx = vertx;
        this.webClient = WebClient.create(vertx);
        this.podmanSocket = podmanSocket;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public void start() throws Exception {
        super.start();
        queryContainers();
        this.timerId =
                vertx.setPeriodic(
                        // TODO make this configurable
                        10_000, unused -> queryContainers());
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        vertx.cancelTimer(timerId);
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        HashMap<String, ContainerSpec> result = new HashMap<>();

        for (ContainerSpec container : containers) {
            result.put(container.Id, container);
        }

        return convert(result.values());
    }

    private void queryContainers() {
        doPodmanRequest(
                l -> {
                    List<ServiceRef> previousRefs = convert(containers);
                    List<ServiceRef> currentRefs = convert(l);

                    ServiceRef.compare(previousRefs).to(currentRefs).updated().stream()
                            .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.MODIFIED, sr));

                    ServiceRef.compare(previousRefs).to(currentRefs).added().stream()
                            .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.FOUND, sr));

                    ServiceRef.compare(previousRefs).to(currentRefs).removed().stream()
                            .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));

                    containers.clear();
                    containers.addAll(l);
                });
    }

    private void doPodmanRequest(Consumer<List<ContainerSpec>> successHandler) {
        URI requestPath = URI.create("http://d/v3.0.0/libpod/containers/json");
        vertx.executeBlocking(
                promise ->
                        webClient
                                .request(
                                        HttpMethod.GET,
                                        podmanSocket,
                                        80,
                                        "localhost",
                                        requestPath.toString())
                                .addQueryParam(
                                        "filters",
                                        gson.toJson(Map.of("label", List.of(CRYOSTAT_LABEL))))
                                .timeout(5_000L)
                                .as(BodyCodec.string())
                                .send(
                                        ar -> {
                                            if (ar.failed()) {
                                                Throwable t = ar.cause();
                                                logger.error("Podman API request failed", t);
                                                promise.fail(t);
                                                return;
                                            }
                                            HttpResponse<String> response = ar.result();
                                            successHandler.accept(
                                                    gson.fromJson(
                                                            response.body(),
                                                            new TypeToken<
                                                                    List<ContainerSpec>>() {}));
                                            promise.complete();
                                        }));
    }

    private ServiceRef convert(ContainerSpec desc) {
        String connectUrl = desc.Labels.get(CRYOSTAT_LABEL);
        URI serviceUrl;
        try {
            serviceUrl = new URI(connectUrl);
        } catch (URISyntaxException e) {
            logger.warn(e);
            return null;
        }

        ServiceRef serviceRef =
                new ServiceRef(
                        null, serviceUrl, Optional.ofNullable(desc.Names.get(0)).orElse(desc.Id));

        Map<AnnotationKey, String> cryostatAnnotations = new HashMap<>();
        cryostatAnnotations.put(AnnotationKey.REALM, REALM);
        if ("service".equals(serviceUrl.getScheme())) {
            try {
                JMXServiceURL jmx = new JMXServiceURL(serviceUrl.toString());
                serviceUrl = URIUtil.getRmiTarget(jmx);
            } catch (URISyntaxException | MalformedURLException e) {
                logger.warn(e);
            }
        }
        cryostatAnnotations.put(AnnotationKey.HOST, serviceUrl.getHost());
        cryostatAnnotations.put(AnnotationKey.PORT, Integer.toString(serviceUrl.getPort()));

        serviceRef.setCryostatAnnotations(cryostatAnnotations);

        serviceRef.setLabels(desc.Labels);

        return serviceRef;
    }

    private List<ServiceRef> convert(Collection<ContainerSpec> descs) {
        return descs.stream().map(this::convert).filter(Objects::nonNull).toList();
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        List<AbstractNode> children = new ArrayList<>();

        Map<String, EnvironmentNode> pods = new HashMap<>();
        for (ContainerSpec container : containers) {
            ServiceRef sr = convert(container);
            if (sr == null) {
                continue;
            }
            TargetNode target = new TargetNode(BaseNodeType.JVM, sr);
            String podName = container.PodName;
            if (StringUtils.isNotBlank(podName)) {
                pods.computeIfAbsent(podName, n -> new EnvironmentNode(n, PodmanNodeType.POD));
                pods.get(podName).addChildNode(target);
            } else {
                children.add(target);
            }
        }
        return new EnvironmentNode(
                REALM, BaseNodeType.REALM, Collections.emptyMap(), pods.values());
    }

    static record PortSpec(
            long container_port, String host_ip, long host_port, String protocol, long range) {}

    static record ContainerSpec(
            String Id,
            String Image,
            Map<String, String> Labels,
            List<String> Names,
            long Pid,
            String Pod,
            String PodName,
            List<PortSpec> Ports,
            long StartedAt,
            String State) {}

    // static record PodSpec(
    //         String Id,
    //         List<ContainerSpec> Containers,
    //         Map<String, String> Labels,
    //         String Name,
    //         String Status) {}

    public enum PodmanNodeType implements NodeType {
        POD("Pod"),
        ;

        private final String label;

        PodmanNodeType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public String getKind() {
            return label;
        }
    }
}
