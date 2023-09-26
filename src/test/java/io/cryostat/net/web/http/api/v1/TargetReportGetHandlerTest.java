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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.reports.SubprocessReportGenerator;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetReportGetHandlerTest {

    TargetReportGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock ReportService reportService;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetReportGetHandler(
                        authManager, credentialsManager, reportService, 30, logger);
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
                    handler.path(),
                    Matchers.equalTo("/api/v1/targets/:targetId/reports/:recordingName"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.READ_TARGET,
                                    ResourceAction.READ_RECORDING,
                                    ResourceAction.CREATE_REPORT,
                                    ResourceAction.READ_REPORT)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.containsInAnyOrder(HttpMimeType.JSON));
        }
    }

    @Nested
    class Behaviour {
        @Mock RoutingContext ctx;
        @Mock HttpServerRequest req;
        @Mock HttpServerResponse resp;

        @BeforeEach
        void setup() {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
            when(ctx.request()).thenReturn(req);
            when(ctx.response()).thenReturn(resp);
            when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);
        }

        @Test
        void shouldHandleRecordingDownloadRequest() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of());
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "");
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldHandleRecordingDownloadRequestFiltered() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "someFilter");
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldHandleRecordingDownloadRequestUnformatted() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "someFilter");
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldRespond404IfRecordingNameNotFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenThrow(
                            new CompletionException(
                                    new RecordingNotFoundException("fooHost:0", "someRecording")));

            when(ctx.pathParam("targetId")).thenReturn("fooHost:0");
            when(ctx.pathParam("recordingName")).thenReturn("someRecording");
            when(ctx.queryParam("filter")).thenReturn(List.of(""));

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond404IfTargetNotFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content =
                    CompletableFuture.failedFuture(
                            new ExecutionException(
                                    new SubprocessReportGenerator
                                            .SubprocessReportGenerationException(
                                            SubprocessReportGenerator.ExitStatus
                                                    .TARGET_CONNECTION_FAILURE)));
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond404IfRecordingNotFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content =
                    CompletableFuture.failedFuture(
                            new ExecutionException(
                                    new SubprocessReportGenerator
                                            .SubprocessReportGenerationException(
                                            SubprocessReportGenerator.ExitStatus
                                                    .NO_SUCH_RECORDING)));
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(content);

            when(ctx.pathParam("targetId")).thenReturn(targetId);
            when(ctx.pathParam("recordingName")).thenReturn(recordingName);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        // @Test
        // void shouldRespond406IfAcceptInvalid() throws Exception {
        //         when(ctx.getAcceptableContentType()).thenReturn(H);

        //     HttpException ex =
        //             Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        //     MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(406));
        // }
    }
}
