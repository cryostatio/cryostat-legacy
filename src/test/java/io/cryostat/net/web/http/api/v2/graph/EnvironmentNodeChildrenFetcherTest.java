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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.AbstractNode;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentNodeChildrenFetcherTest {
    EnvironmentNodeChildrenFetcher fetcher;

    @Mock AuthManager auth;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;

    @BeforeEach
    void setup() {
        this.fetcher = new EnvironmentNodeChildrenFetcher(auth);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldReturnEmptyList() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        EnvironmentNode source = Mockito.mock(EnvironmentNode.class);

        when(env.getSource()).thenReturn(source);
        when(source.getChildren()).thenReturn(Collections.emptyList());

        List<AbstractNode> nodes = fetcher.get(env);

        MatcherAssert.assertThat(nodes, Matchers.notNullValue());
        MatcherAssert.assertThat(nodes, Matchers.empty());
        MatcherAssert.assertThat(nodes, Matchers.instanceOf(List.class));
    }

    @Test
    void shouldReturnChildren() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        EnvironmentNode source = Mockito.mock(EnvironmentNode.class);
        EnvironmentNode leftChildNode = Mockito.mock(EnvironmentNode.class);
        EnvironmentNode rightChildNode = Mockito.mock(EnvironmentNode.class);

        List<AbstractNode> children =
                new ArrayList<AbstractNode>(List.of(leftChildNode, rightChildNode));

        when(env.getSource()).thenReturn(source);
        when(source.getChildren()).thenReturn(children);

        List<AbstractNode> nodes = fetcher.get(env);

        MatcherAssert.assertThat(nodes, Matchers.notNullValue());
        MatcherAssert.assertThat(nodes, Matchers.containsInAnyOrder(leftChildNode, rightChildNode));
    }
}
