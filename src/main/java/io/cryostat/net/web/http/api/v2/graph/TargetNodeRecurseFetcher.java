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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

class TargetNodeRecurseFetcher extends AbstractPermissionedDataFetcher<List<TargetNode>> {

    @Inject
    TargetNodeRecurseFetcher(AuthManager auth) {
        super(auth);
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("EnvironmentNode");
    }

    @Override
    String name() {
        return "descendantTargets";
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
    public List<TargetNode> getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        AbstractNode node = environment.getSource();
        FilterInput filter = FilterInput.from(environment);
        List<TargetNode> result = new ArrayList<>();
        if (node instanceof TargetNode) {
            result.add((TargetNode) node);
        } else if (node instanceof EnvironmentNode) {
            for (AbstractNode child : ((EnvironmentNode) node).getChildren()) {
                DataFetchingEnvironment newEnv =
                        DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment)
                                .source(child)
                                .build();
                result.addAll(get(newEnv));
            }
        } else {
            throw new IllegalStateException(node.getClass().toString());
        }

        if (filter.contains(FilterInput.Key.ID)) {
            int id = filter.get(FilterInput.Key.ID);
            result = result.stream().filter(n -> n.getId() == id).collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.NAME)) {
            String nodeName = filter.get(FilterInput.Key.NAME);
            result =
                    result.stream()
                            .filter(n -> Objects.equals(n.getName(), nodeName))
                            .collect(Collectors.toList());
        }
        if (filter.contains(FilterInput.Key.LABELS)) {
            List<String> labels = filter.get(FilterInput.Key.LABELS);
            for (String label : labels) {
                result =
                        result.stream()
                                .filter(n -> LabelSelectorMatcher.parse(label).test(n.getLabels()))
                                .collect(Collectors.toList());
            }
        }
        if (filter.contains(FilterInput.Key.ANNOTATIONS)) {
            List<String> annotations = filter.get(FilterInput.Key.ANNOTATIONS);
            Function<TargetNode, Map<String, String>> mergedAnnotations =
                    n -> {
                        Map<String, String> merged = new HashMap<>();
                        n.getTarget()
                                .getCryostatAnnotations()
                                .forEach((key, val) -> merged.put(key.name(), val));
                        merged.putAll(n.getTarget().getPlatformAnnotations());
                        return merged;
                    };
            for (String annotation : annotations) {
                result =
                        result.stream()
                                .filter(
                                        n ->
                                                LabelSelectorMatcher.parse(annotation)
                                                        .test(mergedAnnotations.apply(n)))
                                .collect(Collectors.toList());
            }
        }
        return result;
    }
}
