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

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentNodesFetcherTest {
    EnvironmentNodesFetcher fetcher;

    @Mock AuthManager auth;
    @Mock RootNodeFetcher rootNodeFetcher;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher = new EnvironmentNodesFetcher(auth, rootNodeFetcher);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldReturnUniverse() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            EnvironmentNode universe =
                    new EnvironmentNode("Universe", BaseNodeType.UNIVERSE, Collections.emptyMap());

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(rootNodeFetcher.get(env)).thenReturn(universe);

            List<EnvironmentNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(nodes, Matchers.contains(universe));
        }
    }

    @Test
    void shouldReturnUniverseAndChildren() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            EnvironmentNode leftChildNode = Mockito.mock(EnvironmentNode.class);
            EnvironmentNode rightChildNode = Mockito.mock(EnvironmentNode.class);

            EnvironmentNode universe =
                    new EnvironmentNode(
                            "Universe",
                            BaseNodeType.UNIVERSE,
                            Collections.emptyMap(),
                            Set.of(leftChildNode, rightChildNode));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(rootNodeFetcher.get(env)).thenReturn(universe);

            List<EnvironmentNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    nodes, Matchers.containsInAnyOrder(universe, leftChildNode, rightChildNode));
        }
    }

    @Test
    void shouldReturnFilteredEnvironmentNodes() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            EnvironmentNode leftGrandchildNode =
                    new EnvironmentNode("North America", BaseNodeType.REALM);

            EnvironmentNode leftChildNode =
                    new EnvironmentNode(
                            "Earth",
                            BaseNodeType.REALM,
                            Collections.emptyMap(),
                            Set.of(leftGrandchildNode));
            EnvironmentNode rightChildNode = new EnvironmentNode("Mars", BaseNodeType.REALM);

            EnvironmentNode universe =
                    new EnvironmentNode(
                            "Universe",
                            BaseNodeType.UNIVERSE,
                            Collections.emptyMap(),
                            Set.of(leftChildNode, rightChildNode));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NODE_TYPE)).thenReturn(true);
            when(filter.get(FilterInput.Key.NODE_TYPE)).thenReturn(BaseNodeType.REALM.getKind());

            when(rootNodeFetcher.get(env)).thenReturn(universe);

            List<EnvironmentNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    nodes,
                    Matchers.containsInAnyOrder(leftChildNode, rightChildNode, leftGrandchildNode));
        }
    }

    @Test
    void shouldReturnFilteredEnvironmentNodesByNames() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            EnvironmentNode northAmericaNode =
                    new EnvironmentNode("North America", BaseNodeType.REALM);
            EnvironmentNode earthNode = new EnvironmentNode("Earth", BaseNodeType.REALM);
            EnvironmentNode marsNode = new EnvironmentNode("Mars", BaseNodeType.REALM);

            EnvironmentNode universe =
                    new EnvironmentNode(
                            "Universe",
                            BaseNodeType.UNIVERSE,
                            Collections.emptyMap(),
                            Set.of(northAmericaNode, earthNode, marsNode));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAMES)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAMES)).thenReturn(Arrays.asList("Earth", "Mars"));

            when(rootNodeFetcher.get(env)).thenReturn(universe);

            List<EnvironmentNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(nodes, Matchers.containsInAnyOrder(earthNode, marsNode));
        }
    }
}
