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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.reports.SubprocessReportGenerator;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;
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
    @Mock DiscoveryStorage storage;
    @Mock ReportService reportService;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetReportGetHandler(
                        authManager, credentialsManager, storage, reportService, 30, logger);
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
        void shouldUseSecurityContextForTarget() throws Exception {
            String targetId = "fooHost:0";

            RoutingContext ctx = Mockito.mock(RoutingContext.class);
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(ctx.request().headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);

            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(targetId)).thenReturn(Optional.of(sr));
            SecurityContext sc = Mockito.mock(SecurityContext.class);
            Mockito.when(authManager.contextFor(sr)).thenReturn(sc);

            SecurityContext actual = handler.securityContext(ctx);
            MatcherAssert.assertThat(actual, Matchers.sameInstance(sc));
            Mockito.verify(storage).lookupServiceByTargetId(targetId);
            Mockito.verify(authManager).contextFor(sr);
        }

        @Test
        void shouldProduceHtmlAndJson() {
            MatcherAssert.assertThat(
                    handler.produces(),
                    Matchers.containsInAnyOrder(HttpMimeType.HTML, HttpMimeType.JSON));
        }
    }

    @Nested
    class Behaviour {
        @Mock RoutingContext ctx;
        @Mock HttpServerRequest req;
        @Mock HttpServerResponse resp;

        @BeforeEach
        void setup() {
            when(authManager.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
            when(ctx.request()).thenReturn(req);
            when(ctx.response()).thenReturn(resp);
            when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);
        }

        @Test
        void shouldHandleRecordingDownloadRequest() throws Exception {
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of());
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "", true);
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldHandleRecordingDownloadRequestFiltered() throws Exception {
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "someFilter", true);
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldHandleRecordingDownloadRequestUnformatted() throws Exception {
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());

            String targetId = "fooHost:0";
            String recordingName = "foo";
            Future<String> content = CompletableFuture.completedFuture("foobar");
            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            ConnectionDescriptor cd = new ConnectionDescriptor(targetId);

            handler.handle(ctx);

            verify(reportService).get(cd, recordingName, "someFilter", false);
            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("foobar");
        }

        @Test
        void shouldRespond404IfRecordingNameNotFound() throws Exception {
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

            when(reportService.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
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
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

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
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(content);

            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond404IfRecordingNotFound() throws Exception {
            ServiceRef sr = Mockito.mock(ServiceRef.class);
            Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString())).thenReturn(Optional.of(sr));
            Mockito.when(authManager.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());

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
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(content);

            when(ctx.pathParam("targetId")).thenReturn(targetId);
            when(ctx.pathParam("recordingName")).thenReturn(recordingName);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        // FIXME
        // @Test
        // void shouldRespond406IfAcceptInvalid() throws Exception {
        //         when(ctx.getAcceptableContentType()).thenReturn(H);

        //     HttpException ex =
        //             Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        //     MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(406));
        // }
    }
}
