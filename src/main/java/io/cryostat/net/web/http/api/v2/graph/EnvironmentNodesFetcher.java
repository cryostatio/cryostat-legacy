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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.schema.DataFetchingEnvironment;

class EnvironmentNodesFetcher extends AbstractPermissionedDataFetcher<List<EnvironmentNode>> {

    private final RootNodeFetcher rootNodeFetcher;

    @Inject
    EnvironmentNodesFetcher(AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        super(auth);
        this.rootNodeFetcher = rootNodeFetcher;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("Query");
    }

    @Override
    String name() {
        return "environmentNodes";
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
    public List<EnvironmentNode> getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        FilterInput filter = FilterInput.from(environment);
        EnvironmentNode root = rootNodeFetcher.get(environment);
        Set<EnvironmentNode> nodes = flattenEnvNodes(root);

        if (filter.contains(FilterInput.Key.ID)) {
            int id = filter.get(FilterInput.Key.ID);
            nodes = filter(nodes, n -> n.getId() == id);
        }

        if (filter.contains(FilterInput.Key.NAME)) {
            String nodeName = filter.get(FilterInput.Key.NAME);
            nodes = filter(nodes, n -> Objects.equals(n.getName(), nodeName));
        }

        if (filter.contains(FilterInput.Key.NAMES)) {
            List<String> names = filter.get(FilterInput.Key.NAMES);
            nodes = filter(nodes, n -> names.contains(n.getName()));
        }

        if (filter.contains(FilterInput.Key.LABELS)) {
            List<String> labels = filter.get(FilterInput.Key.LABELS);
            for (String label : labels) {
                nodes = filter(nodes, n -> LabelSelectorMatcher.parse(label).test(n.getLabels()));
            }
        }

        if (filter.contains(FilterInput.Key.NODE_TYPE)) {
            String nodeType = filter.get(FilterInput.Key.NODE_TYPE);
            nodes =
                    filter(
                            nodes,
                            n ->
                                    Objects.equals(n.getNodeType().getKind(), nodeType)
                                            || Objects.equals(
                                                    n.getNodeType().toString(), nodeType));
        }
        return new ArrayList<>(nodes);
    }

    Set<EnvironmentNode> flattenEnvNodes(EnvironmentNode root) {
        return new HashSet<>(recurse(root, e -> e instanceof EnvironmentNode));
    }

    Set<EnvironmentNode> recurse(EnvironmentNode node, Function<AbstractNode, Boolean> matcher) {
        Set<EnvironmentNode> result = new HashSet<>();
        if (matcher.apply(node)) {
            result.add(node);
        }
        for (AbstractNode child : node.getChildren()) {
            if (!matcher.apply(child)) {
                continue;
            }
            Set<EnvironmentNode> envChild = recurse((EnvironmentNode) child, matcher);
            result.addAll(envChild);
        }
        return result;
    }

    Set<EnvironmentNode> filter(
            Collection<EnvironmentNode> nodes, Function<EnvironmentNode, Boolean> matcher) {
        Set<EnvironmentNode> result = new HashSet<>();
        for (EnvironmentNode node : nodes) {
            if (matcher.apply(node)) {
                result.add(node);
            }
        }
        return result;
    }
}
