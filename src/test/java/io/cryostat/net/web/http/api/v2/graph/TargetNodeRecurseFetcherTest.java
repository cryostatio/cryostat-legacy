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

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.UnknownNode;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.platform.internal.CustomTargetPlatformClient.CustomTargetNodeType;
import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetNodeRecurseFetcherTest {
    static final String JVM_ID = "id";
    static final String URI_STRING = "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi";
    static final String URI_STRING_2 = "cryostat:9091";
    static final URI EXAMPLE_URI = URI.create(URI_STRING);
    static final URI EXAMPLE_URI_2 = URI.create(URI_STRING_2);
    static final String EXAMPLE_ALIAS = "some.app.Alias";

    TargetNodeRecurseFetcher fetcher;

    @Mock AuthManager auth;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @Mock(answer = RETURNS_SELF)
    DataFetchingEnvironmentImpl.Builder builder;

    @BeforeEach
    void setup() {
        this.fetcher = new TargetNodeRecurseFetcher(auth);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldThrowIllegalStateExceptionOnUnknownNode() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        UnknownNode source = Mockito.mock(UnknownNode.class);

        when(env.getSource()).thenReturn(source);

        IllegalStateException ex =
                Assertions.assertThrows(IllegalStateException.class, () -> fetcher.get(env));
        MatcherAssert.assertThat(ex.getMessage(), Matchers.equalTo(source.getClass().toString()));
    }

    @Test
    void shouldReturnSource() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);

        when(env.getSource()).thenReturn(source);

        List<TargetNode> nodes = fetcher.get(env);

        MatcherAssert.assertThat(nodes, Matchers.notNullValue());
        MatcherAssert.assertThat(nodes, Matchers.contains(source));
    }

    @Test
    void shouldReturnTargetNodes() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(
                            () ->
                                    DataFetchingEnvironmentImpl.newDataFetchingEnvironment(
                                            Mockito.any(DataFetchingEnvironment.class)))
                    .thenReturn(builder);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ServiceRef sharedTarget = new ServiceRef(JVM_ID, EXAMPLE_URI, EXAMPLE_ALIAS);

            TargetNode jdpJvmNode = new TargetNode(BaseNodeType.JVM, sharedTarget);
            TargetNode customTargetNode =
                    new TargetNode(CustomTargetNodeType.CUSTOM_TARGET, sharedTarget);
            TargetNode orphanNode = new TargetNode(KubernetesNodeType.DEPLOYMENT, sharedTarget);

            EnvironmentNode jdpRealm =
                    new EnvironmentNode(
                            "JDP", BaseNodeType.REALM, Collections.emptyMap(), Set.of(jdpJvmNode));
            EnvironmentNode customTargetRealm =
                    new EnvironmentNode(
                            CustomTargetPlatformClient.REALM,
                            BaseNodeType.REALM,
                            Collections.emptyMap(),
                            Set.of(customTargetNode));
            EnvironmentNode universe =
                    new EnvironmentNode(
                            "Universe",
                            BaseNodeType.UNIVERSE,
                            Collections.emptyMap(),
                            Set.of(jdpRealm, customTargetRealm, orphanNode));

            DataFetchingEnvironment jdpEnv = Mockito.mock(DataFetchingEnvironment.class);
            DataFetchingEnvironment customTargetEnv = Mockito.mock(DataFetchingEnvironment.class);
            DataFetchingEnvironment targetNodeEnvs = Mockito.mock(DataFetchingEnvironment.class);

            // Mocking the depth-first order in which the recursion happens (ordered by the
            // SortedSet passed in to universe.children)
            when(builder.build())
                    .thenReturn(
                            jdpEnv,
                            targetNodeEnvs,
                            customTargetEnv,
                            targetNodeEnvs,
                            targetNodeEnvs);

            when(jdpEnv.getGraphQlContext()).thenReturn(graphCtx);
            when(jdpEnv.getSource()).thenReturn(jdpRealm);
            when(customTargetEnv.getGraphQlContext()).thenReturn(graphCtx);
            when(customTargetEnv.getSource()).thenReturn(customTargetRealm);
            when(targetNodeEnvs.getGraphQlContext()).thenReturn(graphCtx);
            when(targetNodeEnvs.getSource()).thenReturn(jdpJvmNode, customTargetNode, orphanNode);

            when(env.getSource()).thenReturn(universe);

            List<TargetNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    nodes, Matchers.containsInAnyOrder(customTargetNode, jdpJvmNode, orphanNode));
        }
    }

    @Test
    void shouldReturnTargetNodeFiltered() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(
                            () ->
                                    DataFetchingEnvironmentImpl.newDataFetchingEnvironment(
                                            Mockito.any(DataFetchingEnvironment.class)))
                    .thenReturn(builder);
            try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
                staticFilter
                        .when(() -> FilterInput.from(Mockito.any(DataFetchingEnvironment.class)))
                        .thenReturn(filter);
                when(env.getGraphQlContext()).thenReturn(graphCtx);
                when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                        .thenReturn(CompletableFuture.completedFuture(true));

                when(filter.contains(Mockito.any())).thenReturn(false);
                when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
                when(filter.get(FilterInput.Key.NAME)).thenReturn(EXAMPLE_URI_2.toString());

                ServiceRef sharedTarget = new ServiceRef(JVM_ID, EXAMPLE_URI, EXAMPLE_ALIAS);

                TargetNode jdpJvmNode = new TargetNode(BaseNodeType.JVM, sharedTarget);
                TargetNode customTargetNode =
                        new TargetNode(CustomTargetNodeType.CUSTOM_TARGET, sharedTarget);
                TargetNode orphanNode =
                        new TargetNode(
                                KubernetesNodeType.DEPLOYMENT,
                                new ServiceRef(JVM_ID, EXAMPLE_URI_2, EXAMPLE_ALIAS));

                EnvironmentNode jdpRealm =
                        new EnvironmentNode(
                                "JDP",
                                BaseNodeType.REALM,
                                Collections.emptyMap(),
                                Set.of(jdpJvmNode));
                EnvironmentNode customTargetRealm =
                        new EnvironmentNode(
                                CustomTargetPlatformClient.REALM,
                                BaseNodeType.REALM,
                                Collections.emptyMap(),
                                Set.of(customTargetNode));
                EnvironmentNode universe =
                        new EnvironmentNode(
                                "Universe",
                                BaseNodeType.UNIVERSE,
                                Collections.emptyMap(),
                                Set.of(jdpRealm, customTargetRealm, orphanNode));

                DataFetchingEnvironment jdpEnv = Mockito.mock(DataFetchingEnvironment.class);
                DataFetchingEnvironment customTargetEnv =
                        Mockito.mock(DataFetchingEnvironment.class);
                DataFetchingEnvironment targetNodeEnvs =
                        Mockito.mock(DataFetchingEnvironment.class);

                // Mocking the depth-first order in which the recursion happens (ordered by the
                // SortedSet passed in to universe.children)
                when(builder.build())
                        .thenReturn(
                                jdpEnv,
                                targetNodeEnvs,
                                customTargetEnv,
                                targetNodeEnvs,
                                targetNodeEnvs);

                when(jdpEnv.getGraphQlContext()).thenReturn(graphCtx);
                when(jdpEnv.getSource()).thenReturn(jdpRealm);
                when(customTargetEnv.getGraphQlContext()).thenReturn(graphCtx);
                when(customTargetEnv.getSource()).thenReturn(customTargetRealm);
                when(targetNodeEnvs.getGraphQlContext()).thenReturn(graphCtx);
                when(targetNodeEnvs.getSource())
                        .thenReturn(jdpJvmNode, customTargetNode, orphanNode);

                when(env.getSource()).thenReturn(universe);

                List<TargetNode> nodes = fetcher.get(env);

                MatcherAssert.assertThat(nodes, Matchers.notNullValue());
                MatcherAssert.assertThat(nodes, Matchers.containsInAnyOrder(orphanNode));
            }
        }
    }
}
