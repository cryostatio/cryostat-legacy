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
import io.cryostat.recordings.RecordingArchiveHelper;
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
    @Mock RecordingArchiveHelper archiveHelper;
    @Mock ReportService reportService;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetFromPathHandler(
                        authManager,
                        credentialsManager,
                        gson,
                        archiveHelper,
                        reportService,
                        30,
                        null);
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
        void shouldProduceHtmlAndRawJson() {
            MatcherAssert.assertThat(
                    handler.produces(),
                    Matchers.containsInAnyOrder(HttpMimeType.HTML, HttpMimeType.JSON_RAW));
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
            when(params.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException(subdirectoryName, recordingName));
            when(reportService.getFromPath(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "", true);
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
            when(params.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "", true);
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
            when(params.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "someFilter", true);
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
            when(params.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            Path fakePath = Mockito.mock(Path.class);

            when(reportService.getFromPath(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(CompletableFuture.completedFuture(fakePath));

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(fakePath));

            verify(reportService).getFromPath(subdirectoryName, recordingName, "someFilter", false);
        }
    }
}
