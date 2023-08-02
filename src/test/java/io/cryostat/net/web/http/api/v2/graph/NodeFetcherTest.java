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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeFetcherTest {
    NodeFetcher fetcher;

    @Mock AuthManager auth;
    @Mock RootNodeFetcher rootNodeFetcher;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;

    @BeforeEach
    void setup() {
        this.fetcher = new NodeFetcher(auth, rootNodeFetcher);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldThrowNoSuchElementException() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        EnvironmentNode universe =
                new EnvironmentNode("Universe", BaseNodeType.UNIVERSE, Collections.emptyMap());

        String name = "Earth";
        String nodeType = BaseNodeType.UNIVERSE.getKind();

        when(rootNodeFetcher.get(env)).thenReturn(universe);
        when(env.getArgument("name")).thenReturn(name);
        when(env.getArgument("nodeType")).thenReturn(nodeType);

        NoSuchElementException ex =
                Assertions.assertThrows(NoSuchElementException.class, () -> fetcher.get(env));
        MatcherAssert.assertThat(
                ex.getMessage(), Matchers.equalTo(String.format("%s named %s", nodeType, name)));
    }

    @Test
    void shouldReturnUniverseNode() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        EnvironmentNode universe =
                new EnvironmentNode("Universe", BaseNodeType.UNIVERSE, Collections.emptyMap());

        when(rootNodeFetcher.get(env)).thenReturn(universe);
        when(env.getArgument("name")).thenReturn("Universe");
        when(env.getArgument("nodeType")).thenReturn(BaseNodeType.UNIVERSE.getKind());

        AbstractNode node = fetcher.get(env);

        MatcherAssert.assertThat(node, Matchers.notNullValue());
        MatcherAssert.assertThat(node, Matchers.equalTo(universe));
    }

    @Test
    void shouldReturnRealmNode() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        EnvironmentNode leftChildNode = Mockito.mock(EnvironmentNode.class);
        EnvironmentNode rightChildNode = Mockito.mock(EnvironmentNode.class);
        lenient().when(leftChildNode.getName()).thenReturn("Earth");
        when(rightChildNode.getName()).thenReturn("Mars");

        // Mockito unnecessary stubbing
        lenient().when(leftChildNode.getNodeType()).thenReturn(BaseNodeType.REALM);
        when(rightChildNode.getNodeType()).thenReturn(BaseNodeType.REALM);

        List<AbstractNode> children =
                new ArrayList<AbstractNode>(List.of(leftChildNode, rightChildNode));

        EnvironmentNode universe =
                new EnvironmentNode(
                        "Universe", BaseNodeType.UNIVERSE, Collections.emptyMap(), children);

        when(rootNodeFetcher.get(env)).thenReturn(universe);
        when(env.getArgument("name")).thenReturn("Mars");
        when(env.getArgument("nodeType")).thenReturn(BaseNodeType.REALM.getKind());

        AbstractNode node = fetcher.get(env);

        MatcherAssert.assertThat(node, Matchers.notNullValue());
        MatcherAssert.assertThat(node, Matchers.equalTo(rightChildNode));
    }
}
