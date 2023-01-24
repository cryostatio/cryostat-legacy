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
package io.cryostat.net.web.http.api.v2;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingNotFoundException;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetReportGetHandlerTest {

    TargetReportGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock DiscoveryStorage storage;
    @Mock ReportService reports;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetReportGetHandler(
                        auth,
                        credentialsManager,
                        jwt,
                        () -> webServer,
                        storage,
                        reports,
                        30,
                        logger);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldUseApiVersion2_1() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_1));
        }

        @Test
        void shouldUseHttpGetVerb() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldUseExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/v2.1/targets/:targetId/reports/:recordingName"));
        }

        @Test
        void shouldRequireResourceActions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            EnumSet.of(
                                    ResourceAction.READ_TARGET,
                                    ResourceAction.READ_RECORDING,
                                    ResourceAction.CREATE_REPORT,
                                    ResourceAction.READ_REPORT)));
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
        void shouldBeOrdered() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Behaviour {
        @Mock RoutingContext ctx;
        @Mock HttpServerResponse resp;
        @Mock JWT token;
        @Mock JWTClaimsSet claims;

        @BeforeEach
        void setup() throws ParseException {
            when(ctx.response()).thenReturn(resp);
            when(resp.putHeader(Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                    .thenReturn(resp);
            when(token.getJWTClaimsSet()).thenReturn(claims);
            when(claims.getStringClaim(Mockito.anyString())).thenReturn(null);
        }

        @Test
        void shouldRespond404IfNotFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");

            Future<String> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("target", "myrecording"));
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
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
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            when(ctx.queryParam("filter")).thenReturn(List.of());

            Future<String> future = CompletableFuture.completedFuture("report text");
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq(""),
                            Mockito.eq(true));
        }

        @Test
        void shouldSendFileIfFoundFiltered() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.HTML.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));

            Future<String> future = CompletableFuture.completedFuture("report text");
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq("someFilter"),
                            Mockito.eq(true));
        }

        @Test
        void shouldSendFileIfFoundUnformatted() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));

            Future<String> future = CompletableFuture.completedFuture("report text");
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyBoolean()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq("someFilter"),
                            Mockito.eq(false));
        }
    }
}
