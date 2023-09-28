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
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetHandler(authManager, credentialsManager, reportService, 30, logger);
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
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            when(reportService.get(Mockito.anyString(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            handler.handle(ctx);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of());
            when(reportService.get(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));
        }

        @Test
        void shouldRespondBySendingFileFiltered() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            when(reportService.get(Mockito.anyString(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            handler.handle(ctx);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            when(reportService.get(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));
        }

        @Test
        void shouldRespondBySendingFileUnformatted() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            when(reportService.get(Mockito.anyString(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(reportService.get(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RecordingNotFoundException(null, "someRecording")));

            Mockito.verify(reportService).get("someRecording", "someFilter");
            Mockito.verify(resp).sendFile(fakePath.toString());
            Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_LENGTH, "12345");
        }

        @Test
        void shouldRespond404IfRecordingNameNotFound() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            when(ctx.parsedHeaders()).thenReturn(phv);
            when(phv.accept()).thenReturn(List.of(header));
            when(header.component()).thenReturn("text");
            when(header.subComponent()).thenReturn("html");

            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);

            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(reportService.get(Mockito.anyString(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RecordingNotFoundException(null, "someRecording")));

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond406IfAcceptInvalid() throws Exception {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
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
