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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.labels.LabelSelectorMatcher;
import io.cryostat.platform.discovery.TargetNode;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

class TargetNodesFetcher extends AbstractPermissionedDataFetcher<List<TargetNode>> {

    private final RootNodeFetcher rootNodeFetcher;
    private final TargetNodeRecurseFetcher recurseFetcher;

    @Inject
    TargetNodesFetcher(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RootNodeFetcher rootNodefetcher,
            TargetNodeRecurseFetcher recurseFetcher) {
        super(auth, credentialsManager);
        this.rootNodeFetcher = rootNodefetcher;
        this.recurseFetcher = recurseFetcher;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("Query");
    }

    @Override
    String name() {
        return "targetNodes";
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
        FilterInput filter = FilterInput.from(environment);
        List<TargetNode> result =
                recurseFetcher.get(
                        DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment)
                                .source(rootNodeFetcher.get(environment))
                                .build());
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

        result.forEach(
                t -> getSessionCredentials(environment, t.getTarget().getServiceUri().toString()));
        return result;
    }
}
