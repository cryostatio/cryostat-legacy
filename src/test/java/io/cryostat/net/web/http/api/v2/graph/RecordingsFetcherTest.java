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

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.inject.Provider;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.TargetConnectionManager.ConnectedTask;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.rules.ArchivedRecordingInfo;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingsFetcherTest {
    static final String URI_STRING = "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi";
    static final String URI_STRING_2 = "cryostat:9091";
    static final URI EXAMPLE_URI = URI.create(URI_STRING);
    static final SelectedField active = Mockito.mock(SelectedField.class);
    static final SelectedField archived = Mockito.mock(SelectedField.class);

    RecordingsFetcher fetcher;

    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingArchiveHelper archiveHelper;
    @Mock RecordingMetadataManager metadataManager;
    @Mock Provider<WebServer> webServer;
    @Mock Logger logger;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;
    @Mock CompletableFuture<List<ArchivedRecordingInfo>> archivedFuture;

    @BeforeAll
    static void init() {
        when(active.getName()).thenReturn("active");
        when(archived.getName()).thenReturn("archived");
    }

    @BeforeEach
    void setup() {
        this.fetcher =
                new RecordingsFetcher(
                        auth,
                        credentialsManager,
                        targetConnectionManager,
                        archiveHelper,
                        metadataManager,
                        webServer,
                        logger);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                fetcher.resourceActions(),
                Matchers.equalTo(
                        Set.of(
                                ResourceAction.READ_TARGET,
                                ResourceAction.READ_RECORDING,
                                ResourceAction.READ_CREDENTIALS)));
    }

    @Test
    void shouldReturnNone() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        DataFetchingFieldSelectionSet selectionSet =
                Mockito.mock(DataFetchingFieldSelectionSet.class);

        when(env.getSource()).thenReturn(source);
        when(env.getSelectionSet()).thenReturn(selectionSet);
        when(selectionSet.getFields()).thenReturn(List.of());

        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(EXAMPLE_URI);

        Recordings recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        // should be null ?
        MatcherAssert.assertThat(recordings.active, Matchers.nullValue());
        MatcherAssert.assertThat(recordings.archived, Matchers.nullValue());
    }

    @Test
    void shouldReturnNoneWithRequestedFields() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(graphCtx.get(RoutingContext.class)).thenReturn(ctx);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        DataFetchingFieldSelectionSet selectionSet =
                Mockito.mock(DataFetchingFieldSelectionSet.class);

        when(env.getSource()).thenReturn(source);
        when(env.getSelectionSet()).thenReturn(selectionSet);
        when(selectionSet.getFields()).thenReturn(List.of(active, archived));

        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(EXAMPLE_URI);

        // mock get active recordings
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any(ConnectedTask.class)))
                .thenReturn(List.of());

        // mock get archived recordings
        when(archiveHelper.getRecordings(Mockito.any())).thenReturn(archivedFuture);
        when(archivedFuture.get()).thenReturn(List.of());

        Recordings recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(recordings.active, Matchers.empty());
        MatcherAssert.assertThat(recordings.archived, Matchers.empty());
    }

    @Test
    void shouldReturnActiveRecording() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
<<<<<<< HEAD
        when(graphCtx.get(RoutingContext.class)).thenReturn(ctx);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
=======
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
>>>>>>> 269950ac (fix test compile, executions still broken)
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        DataFetchingFieldSelectionSet selectionSet =
                Mockito.mock(DataFetchingFieldSelectionSet.class);

        when(env.getSource()).thenReturn(source);
        when(env.getSelectionSet()).thenReturn(selectionSet);
        when(selectionSet.getFields()).thenReturn(List.of(active));

        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(EXAMPLE_URI);

        GraphRecordingDescriptor activeRecording = Mockito.mock(GraphRecordingDescriptor.class);
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any(ConnectedTask.class)))
                .thenReturn(List.of(activeRecording));

        ArchivedRecordingInfo archivedRecording = Mockito.mock(ArchivedRecordingInfo.class);
        lenient().when(archiveHelper.getRecordings(Mockito.any())).thenReturn(archivedFuture);
        lenient().when(archivedFuture.get()).thenReturn(List.of(archivedRecording));

        Recordings recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(recordings.active, Matchers.contains(activeRecording));
        MatcherAssert.assertThat(recordings.archived, Matchers.nullValue());
    }

    @Test
    void shouldReturnArchivedRecording() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        DataFetchingFieldSelectionSet selectionSet =
                Mockito.mock(DataFetchingFieldSelectionSet.class);

        when(env.getSource()).thenReturn(source);
        when(env.getSelectionSet()).thenReturn(selectionSet);
        when(selectionSet.getFields()).thenReturn(List.of(archived));

        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(EXAMPLE_URI);

        GraphRecordingDescriptor activeRecording = Mockito.mock(GraphRecordingDescriptor.class);
        lenient()
                .when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class),
                                Mockito.any(ConnectedTask.class)))
                .thenReturn(List.of(activeRecording));

        ArchivedRecordingInfo archivedRecording = Mockito.mock(ArchivedRecordingInfo.class);
        when(archiveHelper.getRecordings(Mockito.any())).thenReturn(archivedFuture);
        when(archivedFuture.get()).thenReturn(List.of(archivedRecording));

        Recordings recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(recordings.active, Matchers.nullValue());
        MatcherAssert.assertThat(recordings.archived, Matchers.contains(archivedRecording));
    }

    @Test
    void shouldReturnAllRecordings() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
<<<<<<< HEAD
        when(graphCtx.get(RoutingContext.class)).thenReturn(ctx);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
=======
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
>>>>>>> 269950ac (fix test compile, executions still broken)
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        DataFetchingFieldSelectionSet selectionSet =
                Mockito.mock(DataFetchingFieldSelectionSet.class);

        when(env.getSource()).thenReturn(source);
        when(env.getSelectionSet()).thenReturn(selectionSet);
        when(selectionSet.getFields()).thenReturn(List.of(active, archived));

        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(EXAMPLE_URI);

        GraphRecordingDescriptor activeRecording1 = Mockito.mock(GraphRecordingDescriptor.class);
        GraphRecordingDescriptor activeRecording2 = Mockito.mock(GraphRecordingDescriptor.class);
        GraphRecordingDescriptor activeRecording3 = Mockito.mock(GraphRecordingDescriptor.class);
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any(ConnectedTask.class)))
                .thenReturn(List.of(activeRecording1, activeRecording2, activeRecording3));

        ArchivedRecordingInfo archivedRecording1 = Mockito.mock(ArchivedRecordingInfo.class);
        ArchivedRecordingInfo archivedRecording2 = Mockito.mock(ArchivedRecordingInfo.class);
        ArchivedRecordingInfo archivedRecording3 = Mockito.mock(ArchivedRecordingInfo.class);
        when(archiveHelper.getRecordings(Mockito.any())).thenReturn(archivedFuture);
        when(archivedFuture.get())
                .thenReturn(List.of(archivedRecording1, archivedRecording2, archivedRecording3));

        Recordings recordings = fetcher.get(env);

        MatcherAssert.assertThat(recordings, Matchers.notNullValue());
        MatcherAssert.assertThat(
                recordings.active,
                Matchers.containsInAnyOrder(activeRecording1, activeRecording2, activeRecording3));
        MatcherAssert.assertThat(
                recordings.archived,
                Matchers.containsInAnyOrder(
                        archivedRecording1, archivedRecording2, archivedRecording3));
    }
}
