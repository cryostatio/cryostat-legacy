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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.TargetNode;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
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
class TargetNodesFetcherTest {
    TargetNodesFetcher fetcher;

    @Mock AuthManager auth;
    @Mock RootNodeFetcher rootNodeFetcher;
    @Mock TargetNodeRecurseFetcher recurseFetcher;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @Mock(answer = RETURNS_SELF)
    DataFetchingEnvironmentImpl.Builder builder;

    @BeforeEach
    void setup() {
        this.fetcher = new TargetNodesFetcher(auth, rootNodeFetcher, recurseFetcher);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldReturnEmptyList() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(() -> DataFetchingEnvironmentImpl.newDataFetchingEnvironment(env))
                    .thenReturn(builder);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(recurseFetcher.get(Mockito.any())).thenReturn(List.of());

            List<TargetNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(nodes, Matchers.empty());
            MatcherAssert.assertThat(nodes, Matchers.instanceOf(List.class));
        }
    }

    @Test
    void shouldReturnTarget() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(() -> DataFetchingEnvironmentImpl.newDataFetchingEnvironment(env))
                    .thenReturn(builder);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            TargetNode target = Mockito.mock(TargetNode.class);

            when(recurseFetcher.get(Mockito.any())).thenReturn(List.of(target));

            List<TargetNode> nodes = fetcher.get(env);

            MatcherAssert.assertThat(nodes, Matchers.notNullValue());
            MatcherAssert.assertThat(nodes, Matchers.contains(target));
        }
    }

    @Test
    void shouldReturnTargetsFiltered() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(() -> DataFetchingEnvironmentImpl.newDataFetchingEnvironment(env))
                    .thenReturn(builder);
            try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
                staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);

                when(env.getGraphQlContext()).thenReturn(graphCtx);
                when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                        .thenReturn(CompletableFuture.completedFuture(true));

                when(filter.contains(Mockito.any())).thenReturn(false);
                when(filter.contains(FilterInput.Key.NAMES)).thenReturn(true);
                when(filter.get(FilterInput.Key.NAMES)).thenReturn(List.of("foo", "bar", "baz"));

                TargetNode target1 = Mockito.mock(TargetNode.class);
                TargetNode target2 = Mockito.mock(TargetNode.class);
                TargetNode target3 = Mockito.mock(TargetNode.class);
                TargetNode target4 = Mockito.mock(TargetNode.class);
                TargetNode target5 = Mockito.mock(TargetNode.class);

                when(target1.getName()).thenReturn("foo");
                when(target2.getName()).thenReturn("foobar");
                when(target3.getName()).thenReturn("foo");
                when(target4.getName()).thenReturn("bar");
                when(target5.getName()).thenReturn("baz");

                when(recurseFetcher.get(Mockito.any()))
                        .thenReturn(List.of(target1, target2, target3, target4, target5));

                List<TargetNode> nodes = fetcher.get(env);

                MatcherAssert.assertThat(nodes, Matchers.notNullValue());
                MatcherAssert.assertThat(nodes, Matchers.hasItems(target1, target4, target5));
            }
        }
    }

    @Test
    void shouldReturnTargetsMultipleFilters() throws Exception {
        try (MockedStatic<DataFetchingEnvironmentImpl> staticEnv =
                Mockito.mockStatic(DataFetchingEnvironmentImpl.class)) {
            staticEnv
                    .when(() -> DataFetchingEnvironmentImpl.newDataFetchingEnvironment(env))
                    .thenReturn(builder);
            try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
                staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);

                when(env.getGraphQlContext()).thenReturn(graphCtx);
                when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                        .thenReturn(CompletableFuture.completedFuture(true));

                when(filter.contains(Mockito.any())).thenReturn(false);
                when(filter.contains(FilterInput.Key.LABELS)).thenReturn(true);
                when(filter.get(FilterInput.Key.LABELS)).thenReturn(List.of("foo"));

                when(filter.contains(FilterInput.Key.ANNOTATIONS)).thenReturn(true);
                when(filter.get(FilterInput.Key.ANNOTATIONS))
                        .thenReturn(List.of(AnnotationKey.HOST.name(), "open"));

                TargetNode target1 = Mockito.mock(TargetNode.class);
                TargetNode target2 = Mockito.mock(TargetNode.class);
                TargetNode target3 = Mockito.mock(TargetNode.class);

                // label mocking
                when(target1.getLabels()).thenReturn(Map.of("foo", "ear", "", "cat"));
                when(target2.getLabels()).thenReturn(Map.of("ape", "bee", "cat", "foo"));
                when(target3.getLabels()).thenReturn(Map.of("", "", "foo", "bar"));

                // annotation mocking
                ServiceRef ref1 = Mockito.mock(ServiceRef.class);
                ServiceRef ref2 = Mockito.mock(ServiceRef.class);
                ServiceRef ref3 = Mockito.mock(ServiceRef.class);
                when(ref1.getCryostatAnnotations())
                        .thenReturn(
                                Map.of(
                                        AnnotationKey.HOST,
                                        "local",
                                        AnnotationKey.REALM,
                                        "Earth",
                                        AnnotationKey.CONTAINER_NAME,
                                        "cryostat"));
                when(ref1.getPlatformAnnotations()).thenReturn(Map.of("open", "stack"));

                // Mockito unnecessary stubbing
                lenient()
                        .when(ref2.getCryostatAnnotations())
                        .thenReturn(
                                Map.of(AnnotationKey.HOST, "local", AnnotationKey.REALM, "Mars"));
                lenient().when(ref2.getPlatformAnnotations()).thenReturn(Map.of("open", "stack"));

                when(ref3.getCryostatAnnotations())
                        .thenReturn(
                                Map.of(
                                        AnnotationKey.HOST,
                                        "local",
                                        AnnotationKey.REALM,
                                        "Jupiter"));
                when(ref3.getPlatformAnnotations()).thenReturn(Map.of("closed", "heap"));

                when(target1.getTarget()).thenReturn(ref1);
                lenient().when(target2.getTarget()).thenReturn(ref2);
                when(target3.getTarget()).thenReturn(ref3);

                when(recurseFetcher.get(Mockito.any()))
                        .thenReturn(List.of(target1, target2, target3));

                List<TargetNode> nodes = fetcher.get(env);

                MatcherAssert.assertThat(nodes, Matchers.notNullValue());
                MatcherAssert.assertThat(nodes, Matchers.contains(target1));
            }
        }
    }
}
