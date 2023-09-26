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
    @Mock ReportService reports;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetReportGetHandler(
                        auth, credentialsManager, jwt, () -> webServer, reports, 30, logger);
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
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.containsInAnyOrder(HttpMimeType.JSON));
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
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");

            Future<String> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("target", "myrecording"));
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(future);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            when(ctx.queryParam("filter")).thenReturn(List.of());

            Future<String> future = CompletableFuture.completedFuture("report text");
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq(""));
        }

        @Test
        void shouldSendFileIfFoundFiltered() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));

            Future<String> future = CompletableFuture.completedFuture("report text");
            when(reports.get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq("someFilter"));
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
                            Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            verify(resp).end("report text");
            verify(reports)
                    .get(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq("myrecording"),
                            Mockito.eq("someFilter"));
        }
    }
}
