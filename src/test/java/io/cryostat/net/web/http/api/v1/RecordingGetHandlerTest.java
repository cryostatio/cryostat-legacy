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
package io.cryostat.net.web.http.api.v1;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled
@ExtendWith(MockitoExtension.class)
class RecordingGetHandlerTest {

    RecordingGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingGetHandler(
                        authManager, credentialsManager, recordingArchiveHelper, logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/recordings/:recordingName"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldThrow404IfNoMatchingRecordingFound() throws Exception {
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        String recordingName = "foo";
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(recordingName)).thenReturn(future);
        ExecutionException e = Mockito.mock(ExecutionException.class);
        Mockito.when(future.get()).thenThrow(e);
        Mockito.when(e.getCause())
                .thenReturn(new RecordingNotFoundException("archives", recordingName));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldHandleSuccessfulGETRequest() throws Exception {
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        String recordingName = "foo";
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);

        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingPath(recordingName)).thenReturn(future);

        Path archivedRecording = Mockito.mock(Path.class);
        Mockito.when(future.get()).thenReturn(archivedRecording);
        Mockito.when(archivedRecording.toString()).thenReturn("/path/to/recording.jfr");
        File file = Mockito.mock(File.class);
        Mockito.when(archivedRecording.toFile()).thenReturn(file);
        Mockito.when(file.length()).thenReturn(12345L);

        handler.handle(ctx);

        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_LENGTH, "12345");
        Mockito.verify(resp).sendFile(archivedRecording.toString());
    }
}
