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
package io.cryostat.discovery;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.inject.Provider;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;

@Deprecated
/**
 * @deprecated TODO remove this, it's a temporary stub for a database
 */
public class DiscoveryStorage extends AbstractPlatformClient {

    private final Provider<UUID> uuid;
    private final Map<UUID, PluginInfo> map = new HashMap<>();
    private final Logger logger;

    DiscoveryStorage(Provider<UUID> uuid, Logger logger) {
        this.uuid = uuid;
        this.logger = logger;
    }

    @Override
    public void start() throws IOException {
        // TODO persist plugin infos (with empty subtrees) to disk on shutdown, and reinitialize map
        // here. Then, perform POST on each callback URI to check it's still there and prompt it to
        // update us with its subtree
    }

    public UUID register(String realm, URI callback) throws RegistrationException {
        if (map.values().stream().map(PluginInfo::getRealm).anyMatch(realm::equals)) {
            throw new RegistrationException(realm);
        }
        EnvironmentNode subtree = new EnvironmentNode(realm, BaseNodeType.REALM);
        UUID nextId = uuid.get();
        map.put(nextId, new PluginInfo(realm, callback, subtree));
        return nextId;
    }

    public Set<AbstractNode> update(UUID id, Set<AbstractNode> children) {
        try {
            validateId(id);
            EnvironmentNode previousTree = map.get(id).getSubtree();

            PluginInfo updatedInfo = new PluginInfo(map.get(id), children);
            map.put(id, updatedInfo);

            EnvironmentNode currentTree = updatedInfo.getSubtree();

            Set<TargetNode> previousLeaves = findLeavesFrom(previousTree);
            Set<TargetNode> currentLeaves = findLeavesFrom(currentTree);

            Set<TargetNode> added = new HashSet<>(currentLeaves);
            added.removeAll(previousLeaves);

            Set<TargetNode> removed = new HashSet<>(previousLeaves);
            removed.removeAll(currentLeaves);

            added.stream()
                    .map(TargetNode::getTarget)
                    .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.FOUND, sr));
            removed.stream()
                    .map(TargetNode::getTarget)
                    .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));

            return currentTree.getChildren();
        } catch (Exception e) {
            logger.error(e);
            throw e;
        }
    }

    public PluginInfo deregister(UUID id) {
        validateId(id);
        PluginInfo info = map.remove(id);
        findLeavesFrom(info.getSubtree()).stream()
                .map(TargetNode::getTarget)
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));
        return info;
    }

    public EnvironmentNode getDiscoveryTree() {
        EnvironmentNode universe = new EnvironmentNode("Universe", BaseNodeType.UNIVERSE);
        map.values().stream().map(PluginInfo::getSubtree).forEach(universe::addChildNode);
        return universe;
    }

    public Set<TargetNode> getLeafNodes() {
        return findLeavesFrom(getDiscoveryTree());
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return getLeafNodes().stream().map(TargetNode::getTarget).toList();
    }

    private Set<TargetNode> findLeavesFrom(AbstractNode node) {
        if (node instanceof TargetNode) {
            return Set.of((TargetNode) node);
        }
        if (node instanceof EnvironmentNode) {
            EnvironmentNode environment = (EnvironmentNode) node;
            Set<TargetNode> targets = new HashSet<>();
            environment.getChildren().stream().map(this::findLeavesFrom).forEach(targets::addAll);
            return targets;
        }
        throw new IllegalArgumentException(node.getClass().getCanonicalName());
    }

    private void validateId(UUID id) {
        if (!map.containsKey(id)) {
            throw new NotFoundException(id);
        }
    }

    public static class PluginInfo {
        private final String realm;
        private final URI callback;
        private final EnvironmentNode subtree;

        public PluginInfo(String realm, URI callback, EnvironmentNode subtree) {
            this.realm = Objects.requireNonNull(realm);
            this.callback = Objects.requireNonNull(callback);
            this.subtree = new EnvironmentNode(Objects.requireNonNull(subtree));
        }

        public PluginInfo(PluginInfo original, Set<AbstractNode> children) {
            this.realm = original.realm;
            this.callback = original.callback;
            this.subtree =
                    new EnvironmentNode(original.subtree.getName(), original.subtree.getNodeType());
            if (children == null) {
                children = Set.of();
            }
            subtree.addChildren(children);
        }

        public String getRealm() {
            return realm;
        }

        public URI getCallback() {
            return callback;
        }

        public EnvironmentNode getSubtree() {
            return new EnvironmentNode(subtree);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((callback == null) ? 0 : callback.hashCode());
            result = prime * result + ((realm == null) ? 0 : realm.hashCode());
            result = prime * result + ((subtree == null) ? 0 : subtree.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            PluginInfo other = (PluginInfo) obj;
            if (callback == null) {
                if (other.callback != null) return false;
            } else if (!callback.equals(other.callback)) return false;
            if (realm == null) {
                if (other.realm != null) return false;
            } else if (!realm.equals(other.realm)) return false;
            if (subtree == null) {
                if (other.subtree != null) return false;
            } else if (!subtree.equals(other.subtree)) return false;
            return true;
        }
    }

    public static class NotFoundException extends RuntimeException {
        NotFoundException(UUID id) {
            super(String.format("Unknown registration id: [%s]", id.toString()));
        }
    }
}
