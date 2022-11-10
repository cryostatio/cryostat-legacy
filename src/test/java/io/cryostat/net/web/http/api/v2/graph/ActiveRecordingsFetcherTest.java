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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import io.cryostat.configuration.CredentialsManager;
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
    @Mock CredentialsManager credentialsManager;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher = new ActiveRecordingsFetcher(auth, credentialsManager);
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
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
}
