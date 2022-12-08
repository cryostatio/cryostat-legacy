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

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.v2.graph.ArchivedRecordingsFetcher.Archived;
import io.cryostat.recordings.RecordingArchiveHelper;
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
class AllArchivedRecordingsFetcherTest {
    AllArchivedRecordingsFetcher fetcher;

    @Mock AuthManager auth;
    @Mock RecordingArchiveHelper archiveHelper;
    @Mock Logger logger;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;
    @Mock Future<List<ArchivedRecordingInfo>> future;

    @BeforeEach
    void setup() {
        this.fetcher = new AllArchivedRecordingsFetcher(auth, archiveHelper, logger);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldReturnEmptyList() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of());

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.empty());
            MatcherAssert.assertThat(recordings.data, Matchers.instanceOf(List.class));
        }
    }

    @Test
    void shouldReturnRecording() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording = Mockito.mock(ArchivedRecordingInfo.class);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of(recording));

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.contains(recording));
        }
    }

    @Test
    void shouldReturnRecordingsMultiple() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            List<ArchivedRecordingInfo> mockList =
                    List.of(
                            Mockito.mock(ArchivedRecordingInfo.class),
                            Mockito.mock(ArchivedRecordingInfo.class));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.SOURCE_TARGET)).thenReturn(true);

            when(archiveHelper.getRecordings(Mockito.any())).thenReturn(future);
            when(future.get()).thenReturn(mockList);

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.equalTo(mockList));
        }
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

            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of(recording1, recording2, recording3));

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.contains(recording1));
        }
    }

    @Test
    void shouldReturnRecordingsLabelFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);

            when(recording1.getMetadata()).thenReturn(new Metadata(Map.of("foo", "bar")));
            when(recording2.getMetadata())
                    .thenReturn(new Metadata(Map.of("foo", "baz", "bar", "qux")));
            when(recording3.getMetadata())
                    .thenReturn(new Metadata(Map.of("", "foo", "bar", "foo")));

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.LABELS)).thenReturn(true);
            when(filter.get(FilterInput.Key.LABELS)).thenReturn(List.of("foo", "bar"));

            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of(recording1, recording2, recording3));

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.contains(recording2));
        }
    }

    @Test
    void shouldReturnRecordingsSizeFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);

            when(recording1.getSize()).thenReturn(12345L);
            when(recording2.getSize()).thenReturn(123456L);
            when(recording3.getSize()).thenReturn(1234567L);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.SIZE_LE)).thenReturn(true);
            when(filter.get(FilterInput.Key.SIZE_LE)).thenReturn(123456L);

            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of(recording1, recording2, recording3));

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.contains(recording1, recording2));
        }
    }

    @Test
    void shouldReturnRecordingsArchivedTimeFiltered() throws Exception {
        try (MockedStatic<FilterInput> staticFilter = Mockito.mockStatic(FilterInput.class)) {
            staticFilter.when(() -> FilterInput.from(env)).thenReturn(filter);
            when(env.getGraphQlContext()).thenReturn(graphCtx);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            ArchivedRecordingInfo recording1 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording2 = Mockito.mock(ArchivedRecordingInfo.class);
            ArchivedRecordingInfo recording3 = Mockito.mock(ArchivedRecordingInfo.class);

            when(recording1.getArchivedTime()).thenReturn(12345L);
            when(recording2.getArchivedTime()).thenReturn(123456L);
            when(recording3.getArchivedTime()).thenReturn(1234567L);

            when(filter.contains(Mockito.any())).thenReturn(false);
            when(filter.contains(FilterInput.Key.ARCHIVED_TIME_AFTER)).thenReturn(true);
            when(filter.get(FilterInput.Key.ARCHIVED_TIME_AFTER)).thenReturn(123456L);

            when(archiveHelper.getRecordings()).thenReturn(future);
            when(future.get()).thenReturn(List.of(recording1, recording2, recording3));

            Archived recordings = fetcher.get(env);

            MatcherAssert.assertThat(recordings, Matchers.notNullValue());
            MatcherAssert.assertThat(recordings.data, Matchers.contains(recording2, recording3));
        }
    }
}
