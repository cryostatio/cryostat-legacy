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

import java.io.File;
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
import io.cryostat.net.web.http.api.ApiVersion;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled
@ExtendWith(MockitoExtension.class)
class ReportGetHandlerTest {

    ReportGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock ReportService reports;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new ReportGetHandler(
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
                    handler.path(), Matchers.equalTo("/api/v2.1/reports/:recordingName"));
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
        void shouldBeAsync() {
            Assertions.assertTrue(handler.isAsync());
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

        @Test
        void shouldRespond404IfNotFound() throws Exception {
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("archive", "myrecording"));
            Mockito.when(reports.get(Mockito.anyString(), Mockito.anyString())).thenReturn(future);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            Mockito.when(path.toAbsolutePath()).thenReturn(path);
            Mockito.when(path.toString()).thenReturn("foo.jfr");
            File file = Mockito.mock(File.class);
            Mockito.when(path.toFile()).thenReturn(file);
            Mockito.when(file.length()).thenReturn(1234L);
            Future<Path> future = CompletableFuture.completedFuture(path);
            Mockito.when(reports.get(Mockito.anyString(), Mockito.anyString())).thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            Mockito.verify(reports).get("myrecording", "");
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_LENGTH, "1234");
            inOrder.verify(resp).sendFile("foo.jfr");
        }

        @Test
        void shouldSendFileIfFoundFiltered() throws Exception {
            HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            Mockito.when(path.toAbsolutePath()).thenReturn(path);
            Mockito.when(path.toString()).thenReturn("foo.jfr");
            File file = Mockito.mock(File.class);
            Mockito.when(path.toFile()).thenReturn(file);
            Mockito.when(file.length()).thenReturn(1234L);
            Mockito.when(ctx.queryParam("filter")).thenReturn(List.of("someFilter"));
            Future<Path> future = CompletableFuture.completedFuture(path);
            Mockito.when(reports.get(Mockito.anyString(), Mockito.anyString())).thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            Mockito.verify(reports).get("myrecording", "someFilter");
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_LENGTH, "1234");
            inOrder.verify(resp).sendFile("foo.jfr");
        }
    }
}
