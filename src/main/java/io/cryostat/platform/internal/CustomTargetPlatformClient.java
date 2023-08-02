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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;

import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Promise;

public class CustomTargetPlatformClient extends AbstractPlatformClient {

    public static final String REALM = "Custom Targets";
    public static final CustomTargetNodeType NODE_TYPE = CustomTargetNodeType.CUSTOM_TARGET;

    private final Lazy<DiscoveryStorage> storage;
    private final SortedSet<ServiceRef> targets;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Field is never mutated")
    public CustomTargetPlatformClient(Lazy<DiscoveryStorage> storage) {
        this.storage = storage;
        this.targets = new TreeSet<>((u1, u2) -> u1.getServiceUri().compareTo(u2.getServiceUri()));
    }

    @Override
    public void load(Promise<EnvironmentNode> promise) {
        storage.get()
                .getBuiltInPluginByRealm(REALM)
                .ifPresent(
                        plugin -> targets.addAll(storage.get().listDiscoverableServices(plugin)));
        super.load(promise);
    }

    public boolean addTarget(ServiceRef serviceRef) throws IOException {
        boolean v = targets.add(serviceRef);
        if (v) {
            notifyAsyncTargetDiscovery(EventKind.FOUND, serviceRef);
        }
        return v;
    }

    public boolean removeTarget(ServiceRef serviceRef) throws IOException {
        boolean v = targets.remove(serviceRef);
        if (v) {
            notifyAsyncTargetDiscovery(EventKind.LOST, serviceRef);
        }
        return v;
    }

    public boolean removeTarget(URI connectUrl) throws IOException {
        ServiceRef ref = null;
        for (ServiceRef target : targets) {
            if (Objects.equals(connectUrl, target.getServiceUri())) {
                ref = target;
                break;
            }
        }
        if (ref != null) {
            return removeTarget(ref);
        }
        return false;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return new ArrayList<>(targets);
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        List<TargetNode> children =
                targets.stream().map(sr -> new TargetNode(NODE_TYPE, sr)).toList();
        return new EnvironmentNode(REALM, BaseNodeType.REALM, Collections.emptyMap(), children);
    }

    public enum CustomTargetNodeType implements NodeType {
        CUSTOM_TARGET,
        ;

        @Override
        public String getKind() {
            return "CustomTarget";
        }

        @Override
        public String toString() {
            return getKind();
        }
    }
}
