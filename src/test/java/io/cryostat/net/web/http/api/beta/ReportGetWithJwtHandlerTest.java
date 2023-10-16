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
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.containsInAnyOrder(HttpMimeType.JSON));
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
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");

            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("mytarget", "myrecording"));
            when(reports.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(reports.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "");
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            inOrder.verify(resp).sendFile("foo.jfr");
        }

        @Test
        void shouldSendFileIfFoundFiltered() throws Exception {
            when(ctx.getAcceptableContentType()).thenReturn(HttpMimeType.JSON.mime());
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(reports.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "someFilter");
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
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
            when(reports.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            verify(reports).get("mytarget", "myrecording", "someFilter");
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            inOrder.verify(resp).sendFile("foo.jfr");
        }
    }
}
