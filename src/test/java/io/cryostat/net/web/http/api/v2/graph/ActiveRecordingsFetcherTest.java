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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.ActiveRecordingsFetcher.Active;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;

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
class ActiveRecordingsFetcherTest {
    ActiveRecordingsFetcher fetcher;

    @Mock AuthManager auth;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher = new ActiveRecordingsFetcher(auth);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(),
                Matchers.equalTo(
                        Set.of(ResourceAction.READ_RECORDING, ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldReturnEmpty() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Recordings source = Mockito.mock(Recordings.class);
        source.active = List.of();

        when(env.getSource()).thenReturn(source);

        Active active = fetcher.get(env);

        MatcherAssert.assertThat(active, Matchers.notNullValue());
        MatcherAssert.assertThat(active.data, Matchers.empty());
        MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(0L));
    }

    @Test
    void shouldReturnRecording() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Recordings source = Mockito.mock(Recordings.class);
        GraphRecordingDescriptor recording = Mockito.mock(GraphRecordingDescriptor.class);
        source.active = List.of(recording);

        when(env.getSource()).thenReturn(source);

        Active active = fetcher.get(env);

        MatcherAssert.assertThat(active, Matchers.notNullValue());
        MatcherAssert.assertThat(active.data, Matchers.contains(recording));
        MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(1L));
    }

    @Test
    void shouldReturnRecordingsMultiple() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(active.data, Matchers.contains(recording1, recording2));
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(2L));
        }
    }

    @Test
    void shouldReturnRecordingsFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);
            when(recording1.isContinuous()).thenReturn(true);
            when(recording2.isContinuous()).thenReturn(false);
            when(recording3.isContinuous()).thenReturn(true);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.CONTINUOUS)).thenReturn(true);
            when(filter.get(FilterInput.Key.CONTINUOUS)).thenReturn(true);

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(active.data, Matchers.contains(recording1, recording3));
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(2L));
        }
    }

    @Test
    void shouldFilterOutEverything() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);
            when(recording1.getName()).thenReturn("foo");
            when(recording2.getName()).thenReturn("bar");
            when(recording3.getName()).thenReturn("baz");

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAME)).thenReturn("qux");

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(active.data, Matchers.empty());
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(0L));
        }
    }

    @Test
    void shouldFilterOutNothing() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);
            when(recording1.getDuration()).thenReturn(500L);
            when(recording2.getDuration()).thenReturn(750L);
            when(recording3.getDuration()).thenReturn(1000L);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.DURATION_LE)).thenReturn(true);
            when(filter.get(FilterInput.Key.DURATION_LE)).thenReturn(1000L);

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    active.data, Matchers.contains(recording1, recording2, recording3));
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(3L));
        }
    }

    @Test
    void shouldReturnRecordingsMultipleFilters() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);

            // Mockito unnecessary stubbing
            lenient().when(recording1.getStartTime()).thenReturn(500L);
            when(recording2.getStartTime()).thenReturn(750L);
            when(recording3.getStartTime()).thenReturn(1000L);
            when(recording1.getState()).thenReturn(RecordingState.CREATED);
            when(recording2.getState()).thenReturn(RecordingState.RUNNING);
            when(recording3.getState()).thenReturn(RecordingState.RUNNING);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.STATE)).thenReturn(true);
            when(filter.contains(FilterInput.Key.START_TIME_BEFORE)).thenReturn(true);
            when(filter.get(FilterInput.Key.STATE)).thenReturn(RecordingState.RUNNING.toString());
            when(filter.get(FilterInput.Key.START_TIME_BEFORE)).thenReturn(750L);

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(active.data, Matchers.contains(recording2));
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(1L));
        }
    }

    @Test
    void shouldReturnRecordingsFilteredByNames() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);
            when(recording1.getName()).thenReturn("Recording1");
            when(recording2.getName()).thenReturn("Recording2");
            when(recording3.getName()).thenReturn("Recording3");

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAMES)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAMES)).thenReturn(List.of("Recording1", "Recording3"));

            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Active active = fetcher.get(env);

            MatcherAssert.assertThat(active, Matchers.notNullValue());
            MatcherAssert.assertThat(active.data, Matchers.contains(recording1, recording3));
            MatcherAssert.assertThat(active.aggregate.count, Matchers.equalTo(2L));
        }
    }
}
