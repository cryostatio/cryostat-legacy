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
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import com.nimbusds.jwt.JWT;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportGetWithJwtHandlerTest {

    ReportGetWithJwtHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock ReportService reports;
    @Mock RecordingArchiveHelper archiveHelper;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetWithJwtHandler(
                        auth,
                        credentialsManager,
                        jwt,
                        () -> webServer,
                        reports,
                        archiveHelper,
                        30,
                        logger);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldUseApiVersionBeta() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldUseHttpGetVerb() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldRequireResourceActions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            EnumSet.of(
                                    ResourceAction.READ_RECORDING,
                                    ResourceAction.CREATE_REPORT,
                                    ResourceAction.READ_REPORT)));
        }

        @Test
        void shouldUseExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/beta/reports/:sourceTarget/:recordingName/jwt"));
        }

        @Test
        void shouldProduceHtmlAndJson() {
            MatcherAssert.assertThat(
                    handler.produces(),
                    Matchers.containsInAnyOrder(HttpMimeType.HTML, HttpMimeType.JSON));
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
        @Mock JWT token;
        @Mock HttpServerResponse resp;

        @Test
        void shouldRespond404IfNotFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");

            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("mytarget", "myrecording"));
            when(reports.get(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(reports.get(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "", true);
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            inOrder.verify(resp).sendFile("foo.jfr");
        }

        @Test
        void shouldSendFileIfFoundFiltered() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(reports.get(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "someFilter", true);
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            inOrder.verify(resp).sendFile("foo.jfr");
        }

        @Test
        void shouldSendFileIfFoundUnformatted() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(reports.get(
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "someFilter", false);
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            inOrder.verify(resp).sendFile("foo.jfr");
        }
    }
}
