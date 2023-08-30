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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dagger.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

public class DockerPlatformClient extends AbstractPlatformClient {

    public static final String REALM = "Docker";
    public static final String DISCOVERY_LABEL = "io.cryostat.discovery";
    public static final String JMX_URL_LABEL = "io.cryostat.jmxUrl";
    public static final String JMX_HOST_LABEL = "io.cryostat.jmxHost";
    public static final String JMX_PORT_LABEL = "io.cryostat.jmxPort";

    private final Lazy<WebClient> webClient;
    private final Lazy<Vertx> vertx;
    private final SocketAddress dockerSocket;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final Gson gson;
    private final Logger logger;
    private long timerId;

    private final CopyOnWriteArrayList<ContainerSpec> containers = new CopyOnWriteArrayList<>();

    DockerPlatformClient(
            Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            SocketAddress dockerSocket,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Gson gson,
            Logger logger) {
        this.webClient = webClient;
        this.vertx = vertx;
        this.dockerSocket = dockerSocket;
        this.connectionToolkit = connectionToolkit;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public void start() throws Exception {
        super.start();
        queryContainers();
        this.timerId =
                vertx.get()
                        .setPeriodic(
                                // TODO make this configurable
                                10_000, unused -> queryContainers());
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        vertx.get().cancelTimer(timerId);
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
        doDockerListRequest(
                current -> {
                    Set<ContainerSpec> previous = new HashSet<>(containers);
                    Set<ContainerSpec> updated = new HashSet<>(current);

                    Set<ContainerSpec> intersection = new HashSet<>(containers);
                    intersection.retainAll(updated);

                    Set<ContainerSpec> removed = new HashSet<>(previous);
                    removed.removeAll(intersection);

                    Set<ContainerSpec> added = new HashSet<>(updated);
                    added.removeAll(intersection);

                    // does anything ever get modified in this scheme?
                    // notifyAsyncTargetDiscovery(EventKind.MODIFIED, sr);

                    containers.removeAll(removed);
                    removed.stream()
                            .map(this::convert)
                            .filter(Objects::nonNull)
                            .forEach(spec -> notifyAsyncTargetDiscovery(EventKind.LOST, spec));

                    containers.addAll(added);
                    added.stream()
                            .map(this::convert)
                            .filter(Objects::nonNull)
                            .forEach(spec -> notifyAsyncTargetDiscovery(EventKind.FOUND, spec));
                });
    }

    private void doDockerListRequest(Consumer<List<ContainerSpec>> successHandler) {
        URI requestPath = URI.create("http://d/v1.41/containers/json");
        webClient
                .get()
                .request(HttpMethod.GET, dockerSocket, 80, "localhost", requestPath.toString())
                .addQueryParam("filters", gson.toJson(Map.of("label", List.of(DISCOVERY_LABEL))))
                .timeout(2_000L)
                .as(BodyCodec.string())
                .send(
                        ar -> {
                            if (ar.failed()) {
                                Throwable t = ar.cause();
                                logger.error("Docker API request failed", t);
                                return;
                            }
                            successHandler.accept(
                                    gson.fromJson(
                                            ar.result().body(),
                                            new TypeToken<List<ContainerSpec>>() {}));
                        });
    }

    private ServiceRef convert(ContainerSpec desc) {
        try {
            JMXServiceURL connectUrl;
            String hostname;
            int jmxPort;
            if (desc.Labels.containsKey(JMX_URL_LABEL)) {
                connectUrl = new JMXServiceURL(desc.Labels.get(JMX_URL_LABEL));
                if (URIUtil.isRmiUrl(connectUrl)) {
                    URI serviceUrl = URIUtil.getRmiTarget(connectUrl);
                    hostname = serviceUrl.getHost();
                    jmxPort = serviceUrl.getPort();
                } else {
                    hostname = connectUrl.getHost();
                    jmxPort = connectUrl.getPort();
                }
            } else {
                jmxPort = Integer.parseInt(desc.Labels.get(JMX_PORT_LABEL));
                hostname = desc.Labels.get(JMX_HOST_LABEL);
                if (hostname == null) {
                    if (desc.Names.size() < 1) {
                        throw new IndexOutOfBoundsException();
                    }
                    hostname = desc.Names.get(0);
                    if (hostname.startsWith("/")) {
                        hostname = hostname.substring(1);
                    }
                }
                connectUrl = connectionToolkit.get().createServiceURL(hostname, jmxPort);
            }

            Map<AnnotationKey, String> cryostatAnnotations = new HashMap<>();
            cryostatAnnotations.put(AnnotationKey.REALM, REALM);

            cryostatAnnotations.put(AnnotationKey.HOST, hostname);
            cryostatAnnotations.put(AnnotationKey.PORT, Integer.toString(jmxPort));

            ServiceRef serviceRef =
                    new ServiceRef(
                            null,
                            URI.create(connectUrl.toString()),
                            Optional.ofNullable(hostname).orElse(desc.Id));

            serviceRef.setCryostatAnnotations(cryostatAnnotations);
            // TODO perform docker inspection query to populate annotations
            // serviceRef.setPlatformAnnotations();
            serviceRef.setLabels(desc.Labels);

            return serviceRef;
        } catch (NumberFormatException | URISyntaxException | MalformedURLException e) {
            containers.remove(desc);
            logger.warn(e);
            return null;
        }
    }

    private List<ServiceRef> convert(Collection<ContainerSpec> descs) {
        return descs.stream().map(this::convert).filter(Objects::nonNull).toList();
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        List<AbstractNode> children = new ArrayList<>();

        for (ContainerSpec container : containers) {
            ServiceRef sr = convert(container);
            if (sr == null) {
                continue;
            }
            TargetNode target = new TargetNode(BaseNodeType.JVM, sr);
            children.add(target);
        }
        return new EnvironmentNode(REALM, BaseNodeType.REALM, Collections.emptyMap(), children);
    }

    static record PortSpec(
            long container_port, String host_ip, long host_port, String protocol, long range) {}

    static record ContainerSpec(
            String Id,
            String Image,
            Map<String, String> Labels,
            List<String> Names,
            long Pid,
            List<PortSpec> Ports,
            long StartedAt,
            String State) {}

    static record ContainerDetails(Config Config) {}

    static record Config(String Hostname) {}
}
