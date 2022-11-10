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

import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled
@ExtendWith(MockitoExtension.class)
class ReportGetHandlerTest {

    ReportGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock ReportService reportService;
    @Mock RecordingArchiveHelper archiveHelper;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetHandler(
                        authManager, credentialsManager, reportService, archiveHelper, 30, logger);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldHandleGETRequest() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldHandleCorrectPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/v1/reports/:recordingName"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.READ_REPORT,
                                    ResourceAction.CREATE_REPORT,
                                    ResourceAction.READ_RECORDING)));
        }

        @Test
        void shouldNotBeAsync() {
            Assertions.assertFalse(handler.isAsync());
        }

        @Test
        void shouldNotBeOrdered() {
            Assertions.assertFalse(handler.isOrdered());
        }
    }

    @Nested
    class Behaviour {

        @Mock RoutingContext ctx;
        @Mock HttpServerResponse resp;
        @Mock ParsedHeaderValues phv;
        @Mock MIMEHeader header;

        @Test
        void shouldRespondBySendingFile() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of(header));
            when(header.component()).thenReturn("text");
            when(header.subComponent()).thenReturn("html");

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            Path fakePath = Mockito.mock(Path.class);
            Mockito.when(fakePath.toAbsolutePath()).thenReturn(fakePath);
            Mockito.when(fakePath.toString()).thenReturn("/some/fake/path.html");
            File file = Mockito.mock(File.class);
            Mockito.when(fakePath.toFile()).thenReturn(file);
            Mockito.when(file.length()).thenReturn(12345L);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of());
            when(reportService.get(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            handler.handle(ctx);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of());
            when(reportService.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));
        }

        @Test
        void shouldRespondBySendingFileFiltered() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of(header));
            when(header.component()).thenReturn("text");
            when(header.subComponent()).thenReturn("html");

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            Path fakePath = Mockito.mock(Path.class);
            Mockito.when(fakePath.toAbsolutePath()).thenReturn(fakePath);
            Mockito.when(fakePath.toString()).thenReturn("/some/fake/path.html");
            File file = Mockito.mock(File.class);
            Mockito.when(fakePath.toFile()).thenReturn(file);
            Mockito.when(file.length()).thenReturn(12345L);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            when(reportService.get(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            handler.handle(ctx);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            when(reportService.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));
        }

        @Test
        void shouldRespondBySendingFileUnformatted() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of(header));
            when(header.component()).thenReturn("application");
            when(header.subComponent()).thenReturn("json");

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            Path fakePath = Mockito.mock(Path.class);
            Mockito.when(fakePath.toAbsolutePath()).thenReturn(fakePath);
            Mockito.when(fakePath.toString()).thenReturn("/some/fake/path.json");
            File file = Mockito.mock(File.class);
            Mockito.when(fakePath.toFile()).thenReturn(file);
            Mockito.when(file.length()).thenReturn(12345L);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            when(reportService.get(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(reportService.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RecordingNotFoundException(null, "someRecording")));

            Mockito.verify(reportService).get("someRecording", "someFilter", false);
            Mockito.verify(resp).sendFile(fakePath.toString());
            Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_LENGTH, "12345");
        }

        @Test
        void shouldRespond404IfRecordingNameNotFound() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of(header));
            when(header.component()).thenReturn("text");
            when(header.subComponent()).thenReturn("html");

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(reportService.get(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RecordingNotFoundException(null, "someRecording")));

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond406IfAcceptInvalid() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of());

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(406));
        }
    }
}
