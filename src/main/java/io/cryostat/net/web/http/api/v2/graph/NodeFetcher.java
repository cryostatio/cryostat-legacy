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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.schema.DataFetchingEnvironment;

class NodeFetcher extends AbstractPermissionedDataFetcher<AbstractNode> {

    private final RootNodeFetcher rootNodeFetcher;

    @Inject
    NodeFetcher(AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        super(auth);
        this.rootNodeFetcher = rootNodeFetcher;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("EnvironmentNode");
    }

    @Override
    String name() {
        return "find";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET);
    }

    @Override
    boolean blocking() {
        return false;
    }

    @Override
    public AbstractNode getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        EnvironmentNode root = rootNodeFetcher.get(environment);
        String name = environment.getArgument("name");
        String nodeType = environment.getArgument("nodeType");
        AbstractNode node = findNode(name, nodeType, root);
        if (node == null) {
            throw new NoSuchElementException(String.format("%s named %s", nodeType, name));
        }
        return node;
    }

    static AbstractNode findNode(String name, String nodeType, AbstractNode root) {
        if (Objects.equals(name, root.getName())
                && root.getNodeType().getKind().equalsIgnoreCase(nodeType)) {
            return root;
        }
        if (root instanceof EnvironmentNode) {
            for (AbstractNode child : ((EnvironmentNode) root).getChildren()) {
                AbstractNode found = findNode(name, nodeType, child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
