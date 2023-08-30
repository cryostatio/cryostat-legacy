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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.DiscoveredJvmDescriptor;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.JvmDiscoveryEvent;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.URIUtil;

public class DefaultPlatformClient extends AbstractPlatformClient
        implements Consumer<JvmDiscoveryEvent> {

    public static final String REALM = "JDP";

    public static final NodeType NODE_TYPE = BaseNodeType.JVM;

    private final Logger logger;
    private final JvmDiscoveryClient discoveryClient;

    DefaultPlatformClient(Logger logger, JvmDiscoveryClient discoveryClient) {
        this.logger = logger;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public void start() throws IOException {
        discoveryClient.addListener(this);
        discoveryClient.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        discoveryClient.removeListener(this);
        discoveryClient.stop();
    }

    @Override
    public void accept(JvmDiscoveryEvent evt) {
        try {
            notifyAsyncTargetDiscovery(evt.getEventKind(), convert(evt.getJvmDescriptor()));
        } catch (MalformedURLException | URISyntaxException e) {
            logger.warn(e);
        }
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return discoveryClient.getDiscoveredJvmDescriptors().stream()
                .map(
                        desc -> {
                            try {
                                return convert(desc);
                            } catch (MalformedURLException | URISyntaxException e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    private static ServiceRef convert(DiscoveredJvmDescriptor desc)
            throws MalformedURLException, URISyntaxException {
        JMXServiceURL serviceUrl = desc.getJmxServiceUrl();
        ServiceRef serviceRef =
                new ServiceRef(null, URIUtil.convert(serviceUrl), desc.getMainClass());
        URI rmiTarget = URIUtil.getRmiTarget(serviceUrl);
        serviceRef.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        REALM,
                        AnnotationKey.JAVA_MAIN,
                        desc.getMainClass(),
                        AnnotationKey.HOST,
                        rmiTarget.getHost(),
                        AnnotationKey.PORT,
                        Integer.toString(rmiTarget.getPort())));
        return serviceRef;
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        List<TargetNode> targets =
                listDiscoverableServices().stream()
                        .map(sr -> new TargetNode(NODE_TYPE, sr))
                        .toList();
        return new EnvironmentNode(REALM, BaseNodeType.REALM, Collections.emptyMap(), targets);
    }
}
