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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.vertx.ext.web.RoutingContext;

@ExtendWith(MockitoExtension.class)
public class ActiveRecordingsFetcherTest {
    ActiveRecordingsFetcher fetcher;

    @Mock AuthManager authManager;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher =
                new ActiveRecordingsFetcher(authManager);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING, ResourceAction.READ_TARGET)));
    }

    @Test 
    void shouldReturnEmptyList() throws Exception {
        Recordings source = Mockito.mock(Recordings.class);
        source.active = List.of();
        
        Mockito.when(env.getSource()).thenReturn(source);
        Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(true));

        List<GraphRecordingDescriptor> recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(recordings, Matchers.empty());
        MatcherAssert.assertThat(recordings, Matchers.instanceOf(List.class));
    }

    @Test 
    void shouldReturnRecording() throws Exception {
        Recordings source = Mockito.mock(Recordings.class);
        GraphRecordingDescriptor recording = Mockito.mock(GraphRecordingDescriptor.class);
        source.active = List.of(recording);
        
        Mockito.when(env.getSource()).thenReturn(source);
        Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(true));

        List<GraphRecordingDescriptor> recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(recordings, Matchers.hasSize(1));
        MatcherAssert.assertThat(recordings.get(0), Matchers.equalTo(recording));
    }

    @Test 
    void shouldReturnRecordingsMultiple() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env))
            .thenReturn(filter);

            Recordings source = Mockito.mock(Recordings.class);
            List<GraphRecordingDescriptor> list = List.of(Mockito.mock(GraphRecordingDescriptor.class), Mockito.mock(GraphRecordingDescriptor.class));
            source.active = list;
            
            Mockito.when(env.getSource()).thenReturn(source);
            Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
            Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    
            List<GraphRecordingDescriptor> recordings = fetcher.get(env);
    
            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings, Matchers.hasSize(2));
            MatcherAssert.assertThat(recordings, Matchers.equalTo(list));
        }
    }

    @Test 
    void shouldReturnRecordingsFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env))
            .thenReturn(filter);
            
            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);        
            Mockito.when(recording1.isContinuous()).thenReturn(true);
            Mockito.when(recording2.isContinuous()).thenReturn(false);
            Mockito.when(recording3.isContinuous()).thenReturn(true);

            Mockito.when(filter.contains(Mockito.any())).thenReturn(false);
            Mockito.when(filter.contains(FilterInput.Key.CONTINUOUS)).thenReturn(true);
            Mockito.when(filter.get(FilterInput.Key.CONTINUOUS)).thenReturn(true);
    
            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);
            
            Mockito.when(env.getSource()).thenReturn(source);
            Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
            Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    
            List<GraphRecordingDescriptor> recordings = fetcher.get(env);
    
            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings, Matchers.hasSize(2));
            MatcherAssert.assertThat(recordings, Matchers.equalTo(List.of(recording1, recording3)));
        }
    }

    @Test 
    void shouldFilterOutEverything() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env))
            .thenReturn(filter);
            
            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);        
            Mockito.when(recording1.getName()).thenReturn("foo");
            Mockito.when(recording2.getName()).thenReturn("bar");
            Mockito.when(recording3.getName()).thenReturn("baz");

            Mockito.when(filter.contains(Mockito.any())).thenReturn(false);
            Mockito.when(filter.contains(FilterInput.Key.NAME)).thenReturn(true);
            Mockito.when(filter.get(FilterInput.Key.NAME)).thenReturn("qux");
    
            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);
            
            Mockito.when(env.getSource()).thenReturn(source);
            Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
            Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    
            List<GraphRecordingDescriptor> recordings = fetcher.get(env);
    
            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings, Matchers.empty());
        }
    }

    @Test 
    void shouldFilterOutNothing() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env))
            .thenReturn(filter);
            
            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);        
            Mockito.when(recording1.getDuration()).thenReturn(500L);
            Mockito.when(recording2.getDuration()).thenReturn(750L);
            Mockito.when(recording3.getDuration()).thenReturn(1000L);

            Mockito.when(filter.contains(Mockito.any())).thenReturn(false);
            Mockito.when(filter.contains(FilterInput.Key.DURATION_LE)).thenReturn(true);
            Mockito.when(filter.get(FilterInput.Key.DURATION_LE)).thenReturn(1000L);
    
            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);
            
            Mockito.when(env.getSource()).thenReturn(source);
            Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
            Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    
            List<GraphRecordingDescriptor> recordings = fetcher.get(env);
    
            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings, Matchers.hasSize(3));
            MatcherAssert.assertThat(recordings, Matchers.equalTo(List.of(recording1, recording2, recording3)));
        }
    }

    @Test 
    void shouldReturnRecordingsMultipleFilters() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env))
            .thenReturn(filter);
            
            GraphRecordingDescriptor recording1 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording2 = Mockito.mock(GraphRecordingDescriptor.class);
            GraphRecordingDescriptor recording3 = Mockito.mock(GraphRecordingDescriptor.class);        
            // Mockito.when(recording1.getStartTime()).thenReturn(500L); // unnecessary stubbing since Key.STATE is checked first
            Mockito.when(recording2.getStartTime()).thenReturn(750L);
            Mockito.when(recording3.getStartTime()).thenReturn(1000L);
            Mockito.when(recording1.getState()).thenReturn(RecordingState.CREATED);
            Mockito.when(recording2.getState()).thenReturn(RecordingState.RUNNING);
            Mockito.when(recording3.getState()).thenReturn(RecordingState.RUNNING);

            Mockito.when(filter.contains(Mockito.any())).thenReturn(false);
            Mockito.when(filter.contains(FilterInput.Key.STATE)).thenReturn(true);
            Mockito.when(filter.contains(FilterInput.Key.START_TIME_BEFORE)).thenReturn(true);
            Mockito.when(filter.get(FilterInput.Key.STATE)).thenReturn(RecordingState.RUNNING.toString());
            Mockito.when(filter.get(FilterInput.Key.START_TIME_BEFORE)).thenReturn(750L);
    
            Recordings source = Mockito.mock(Recordings.class);
            source.active = List.of(recording1, recording2, recording3);
            
            Mockito.when(env.getSource()).thenReturn(source);
            Mockito.when(env.getGraphQlContext()).thenReturn(graphCtx);
            Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    
            List<GraphRecordingDescriptor> recordings = fetcher.get(env);
    
            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings, Matchers.hasSize(1));
            MatcherAssert.assertThat(recordings, Matchers.equalTo(List.of(recording2)));
        }
    }
}
