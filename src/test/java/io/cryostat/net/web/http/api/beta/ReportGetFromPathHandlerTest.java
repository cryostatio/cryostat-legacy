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
package io.cryostat.net.web.http.api.beta;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingNotFoundException;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
class ReportGetFromPathHandlerTest {

    ReportGetFromPathHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock Gson gson;
    @Mock ReportService reportService;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetFromPathHandler(
                        authManager, credentialsManager, gson, reportService, 30);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeBetaHandler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHandleGETRequest() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
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
        void shouldHandleCorrectPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/beta/fs/reports/:subdirectoryName/:recordingName"));
        }

        @Test
        void shouldProduceRawJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.containsInAnyOrder(HttpMimeType.JSON_RAW));
        }

        @Test
        void shouldNotBeAsync() {
            Assertions.assertFalse(handler.isAsync());
        }
    }

    @Nested
    class Behaviour {

        @Mock RequestParameters params;

        @Test
        void shouldThrow404IfNoMatchingRecordingFound() throws Exception {
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            String recordingName = "someRecording";
            String subdirectoryName = "someDirectory";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(params.getQueryParams()).thenReturn(queryParams);

            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException(subdirectoryName, recordingName));
            when(reportService.getFromPath(
                            Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "");
        }

        @Test
        void shouldRespondBySendingFile() throws Exception {
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            String recordingName = "someRecording";
            String subdirectoryName = "subdirectoryName";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(params.getQueryParams()).thenReturn(queryParams);

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "");
        }

        @Test
        void shouldRespondBySendingFileFiltered() throws Exception {
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.add("filter", "someFilter");
            String recordingName = "someRecording";
            String subdirectoryName = "subdirectoryName";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(params.getQueryParams()).thenReturn(queryParams);

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "someFilter");
        }

        @Test
        void shouldRespondBySendingFileUnformatted() throws Exception {
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.add("filter", "someFilter");
            String recordingName = "someRecording";
            String subdirectoryName = "subdirectoryName";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of(
                                    "subdirectoryName",
                                    subdirectoryName,
                                    "recordingName",
                                    recordingName));
            when(params.getQueryParams()).thenReturn(queryParams);

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "someFilter");
        }
    }
}
