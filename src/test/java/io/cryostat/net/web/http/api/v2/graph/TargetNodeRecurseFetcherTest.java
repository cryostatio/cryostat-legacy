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

            ServiceRef sharedTarget = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);

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

                ServiceRef sharedTarget = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);

                TargetNode jdpJvmNode = new TargetNode(BaseNodeType.JVM, sharedTarget);
                TargetNode customTargetNode =
                        new TargetNode(CustomTargetNodeType.CUSTOM_TARGET, sharedTarget);
                TargetNode orphanNode =
                        new TargetNode(
                                KubernetesNodeType.DEPLOYMENT,
                                new ServiceRef(EXAMPLE_URI_2, EXAMPLE_ALIAS));

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
