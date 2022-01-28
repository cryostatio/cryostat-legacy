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
package io.cryostat.net.web.http.api.beta.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import javax.inject.Inject;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;

class EnvironmentNodesFetcher implements DataFetcher<List<EnvironmentNode>> {

    private final DiscoveryFetcher discoveryFetcher;
    private final EnvironmentNodeRecurseFetcher recurseFetcher;

    @Inject
    EnvironmentNodesFetcher(
            DiscoveryFetcher discoveryFetcher, EnvironmentNodeRecurseFetcher recurseFetcher) {
        this.discoveryFetcher = discoveryFetcher;
        this.recurseFetcher = recurseFetcher;
    }

    @Override
    public List<EnvironmentNode> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, String> filter = environment.getArgument("filter");
        EnvironmentNode root = discoveryFetcher.get(environment);
        List<EnvironmentNode> nodes = recurseFetcher.get(
                DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment)
                .source(root)
                .build());

        if (filter == null) {
            return nodes;
        }

        if (filter.containsKey("nodeName")) {
            String nodeName = filter.get("nodeName");
            List<EnvironmentNode> namedNodes = recurse(root, n -> Objects.equals(n.getName(), nodeName));
            if (namedNodes.isEmpty()) {
                throw new NoSuchElementException(String.format("Node named %s", nodeName));
            }
            return namedNodes;
        }

        if (filter.containsKey("nodeType")) {
            String nodeType = filter.get("nodeType");
            List<EnvironmentNode> typedNodes = recurse(root, n -> n.getNodeType().getKind().equalsIgnoreCase(nodeType));
            if (typedNodes.isEmpty()) {
                throw new NoSuchElementException(String.format("Node of type %s", nodeType));
            }
            return typedNodes;
        }

        if (filter.containsKey("labelKey") && filter.containsKey("labelValue")) {
            String labelKey = filter.get("labelKey");
            String labelValue = filter.get("labelValue");
            List<EnvironmentNode> labelledNodes = recurse(root, n -> {
                Map<String, String> labels = n.getLabels();
                return Objects.equals(labels.get(labelKey), labelValue);
            });
            if (labelledNodes.isEmpty()) {
                throw new NoSuchElementException(String.format("Node with label %s=%s", labelKey,
                            labelValue));
            }
            return labelledNodes;
        }

        throw new UnsupportedOperationException(filter.toString());
    }

    List<EnvironmentNode> recurse(EnvironmentNode node, Function<EnvironmentNode, Boolean> matcher) {
        List<EnvironmentNode> result = new ArrayList<>();
        if (matcher.apply(node)) {
            result.add(node);
            return result;
        }
        for (AbstractNode child : node.getChildren()) {
            if (child instanceof TargetNode) {
                continue;
            }
            List<EnvironmentNode> envChild = recurse((EnvironmentNode) child, matcher);
            result.addAll(envChild);
        }
        return result;
    }
}
