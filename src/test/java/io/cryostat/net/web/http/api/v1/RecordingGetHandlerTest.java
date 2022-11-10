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
import io.cryostat.recordings.RecordingMetadataManager;
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
    @Mock RecordingMetadataManager metadataManager;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingGetHandler(
                        authManager,
                        credentialsManager,
                        recordingArchiveHelper,
                        metadataManager,
                        logger);
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
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
        Mockito.when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
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
