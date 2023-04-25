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
