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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.ArchivedRecordingsFetcher.Archived;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

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
class ArchivedRecordingsFetcherTest {
    ArchivedRecordingsFetcher fetcher;

    @Mock AuthManager auth;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher = new ArchivedRecordingsFetcher(auth);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldReturnEmpty() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Recordings source = Mockito.mock(Recordings.class);
        source.archived = List.of();

        when(env.getSource()).thenReturn(source);

        Archived archived = fetcher.get(env);

        MatcherAssert.assertThat(archived, Matchers.notNullValue());
        MatcherAssert.assertThat(archived.data, Matchers.empty());
        MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(0L));
    }

    @Test
    void shouldReturnRecording() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ArchivedRecordingInfo recording = Mockito.mock(ArchivedRecordingInfo.class);

        Recordings source = Mockito.mock(Recordings.class);
        source.archived = List.of(recording);

        when(env.getSource()).thenReturn(source);

        Archived archived = fetcher.get(env);

        MatcherAssert.assertThat(archived, Matchers.notNullValue());
        MatcherAssert.assertThat(archived.data, Matchers.contains(recording));
        MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(1L));
    }

    @Test
    void shouldReturnRecordingsMultiple() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
        ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
        ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);

        Recordings source = Mockito.mock(Recordings.class);
        source.archived = List.of(recording1, recording2, recording3);

        when(env.getSource()).thenReturn(source);

        Archived archived = fetcher.get(env);

        MatcherAssert.assertThat(archived, Matchers.notNullValue());
        MatcherAssert.assertThat(
                archived.data, Matchers.contains(recording1, recording2, recording3));
        MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(3L));
    }

    @Test
    void shouldReturnRecordingsFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);
            when(recording1.getName()).thenReturn("foo");
            when(recording2.getName()).thenReturn("bar");
            when(recording3.getName()).thenReturn("baz");

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAME)).thenReturn("foo");

            Recordings source = Mockito.mock(Recordings.class);
            source.archived = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Archived archived = fetcher.get(env);

            MatcherAssert.assertThat(archived, Matchers.notNullValue());
            MatcherAssert.assertThat(archived.data, Matchers.contains(recording1));
            MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(1L));
        }
    }

    @Test
    void shouldReturnRecordingsMultipleFilters() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            String nameFilter = "foo";
            String labelFilter1 = "template.type";
            String labelFilter2 = "myLabel";
            Long size = 1234567L;

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording4 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording5 = Mockito.mock(ArchivedRecordingInfo.class);
            when(recording1.getName()).thenReturn("foo");
            when(recording2.getName()).thenReturn("bar");
            when(recording3.getName()).thenReturn("foo");
            when(recording4.getName()).thenReturn("baz");
            when(recording5.getName()).thenReturn("foo");
            when(recording1.getMetadata())
                    .thenReturn(
                            new Metadata(Map.of("myLabel", "bar", "template.name", "Cryostat")));
            lenient()
                    .when(recording2.getMetadata())
                    .thenReturn(new Metadata(Map.of("template.type", "CUSTOM", "", "")));
            when(recording3.getMetadata())
                    .thenReturn(new Metadata(Map.of("template.type", "TARGET", "myLabel", "foo")));
            lenient()
                    .when(recording4.getMetadata())
                    .thenReturn(
                            new Metadata(Map.of("myLabel", "value", "reason", "service-outage")));
            when(recording5.getMetadata())
                    .thenReturn(
                            new Metadata(Map.of("myLabel", "foo", "template.type", "Profiling")));
            when(recording3.getSize()).thenReturn(1234577L);
            when(recording5.getSize()).thenReturn(1234569L);
            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
            when(filter.contains(FilterInput.Key.LABELS)).thenReturn(true);
            when(filter.contains(FilterInput.Key.SIZE_GE)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAME)).thenReturn(nameFilter);
            when(filter.get(FilterInput.Key.SIZE_GE)).thenReturn(size);
            when(filter.get(FilterInput.Key.LABELS))
                    .thenReturn(List.of(labelFilter1, labelFilter2));

            Recordings source = Mockito.mock(Recordings.class);
            source.archived = List.of(recording1, recording2, recording3, recording4, recording5);

            when(env.getSource()).thenReturn(source);

            Archived archived = fetcher.get(env);

            MatcherAssert.assertThat(archived, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    archived.data, Matchers.containsInAnyOrder(recording3, recording5));
            MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(2L));
            MatcherAssert.assertThat(
                    archived.aggregate.size,
                    Matchers.equalTo(recording3.getSize() + recording5.getSize()));
        }
    }

    @Test
    void shouldReturnArchivedRecordingsFilteredByNames() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);
            when(recording1.getName()).thenReturn("foo");
            when(recording2.getName()).thenReturn("bar");
            when(recording3.getName()).thenReturn("baz");

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.NAMES)).thenReturn(true);
            when(filter.get(FilterInput.Key.NAMES)).thenReturn(List.of("foo", "baz"));

            Recordings source = Mockito.mock(Recordings.class);
            source.archived = List.of(recording1, recording2, recording3);

            when(env.getSource()).thenReturn(source);

            Archived archived = fetcher.get(env);

            MatcherAssert.assertThat(archived, Matchers.notNullValue());
            MatcherAssert.assertThat(
                    archived.data, Matchers.containsInAnyOrder(recording1, recording3));
            MatcherAssert.assertThat(archived.aggregate.count, Matchers.equalTo(2L));
        }
    }
}
