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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public Optional<ServiceRef> getByUri(URI connectUrl) {
        return targets.stream().filter(t -> t.getServiceUri().equals(connectUrl)).findFirst();
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
