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
