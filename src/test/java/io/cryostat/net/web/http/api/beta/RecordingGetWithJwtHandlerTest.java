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

import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
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
class RecordingGetWithJwtHandlerTest {

    RecordingGetWithJwtHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock RecordingArchiveHelper archive;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingGetWithJwtHandler(
                        auth, credentialsManager, jwt, () -> webServer, archive, logger);
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
        void shouldUseExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/beta/recordings/:sourceTarget/:recordingName/jwt"));
        }

        @Test
        void shouldRequireResourceActions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(EnumSet.of(ResourceAction.READ_RECORDING)));
        }

        @Test
        void shouldBeAsync() {
            Assertions.assertTrue(handler.isAsync());
        }

        @Test
        void shouldBeOrdered() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Behaviour {

        @Mock RoutingContext ctx;
        @Mock JWT token;

        @Test
        void shouldRespond404IfNotFound() throws Exception {
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException("mytarget", "myrecording"));
            when(archive.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
            when(ctx.response()).thenReturn(resp);
            when(ctx.pathParam("sourceTarget")).thenReturn("mytarget");
            when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            Path path = Mockito.mock(Path.class);
            when(path.toAbsolutePath()).thenReturn(path);
            when(path.toString()).thenReturn("foo.jfr");
            Future<Path> future = CompletableFuture.completedFuture(path);
            when(archive.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            handler.handleWithValidJwt(ctx, token);

            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp)
                    .putHeader(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"myrecording\"");
            inOrder.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
            inOrder.verify(resp).sendFile("foo.jfr");
        }
    }
}
