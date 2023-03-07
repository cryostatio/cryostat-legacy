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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.configuration.CredentialsManager;
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
    @Mock CredentialsManager credentialsManager;
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
        this.fetcher =
                new TargetNodesFetcher(auth, credentialsManager, rootNodeFetcher, recurseFetcher);
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
                when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
                when(filter.get(FilterInput.Key.NAME)).thenReturn("foo");

                TargetNode target1 = Mockito.mock(TargetNode.class);
                TargetNode target2 = Mockito.mock(TargetNode.class);
                TargetNode target3 = Mockito.mock(TargetNode.class);

                when(target1.getName()).thenReturn("foo");
                when(target2.getName()).thenReturn("foobar");
                when(target3.getName()).thenReturn("foo");

                when(recurseFetcher.get(Mockito.any()))
                        .thenReturn(List.of(target1, target2, target3));

                List<TargetNode> nodes = fetcher.get(env);

                MatcherAssert.assertThat(nodes, Matchers.notNullValue());
                MatcherAssert.assertThat(nodes, Matchers.containsInAnyOrder(target1, target3));
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
