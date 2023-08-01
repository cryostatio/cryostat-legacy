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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.HttpServer;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetRecordingGetHandlerTest {

    TargetRecordingGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        Mockito.when(httpServer.getVertx()).thenReturn(vertx);
        this.handler =
                new TargetRecordingGetHandler(
                        auth,
                        credentialsManager,
                        jwt,
                        () -> webServer,
                        httpServer,
                        targetConnectionManager,
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
                    Matchers.equalTo("/api/v2.1/targets/:targetId/recordings/:recordingName"));
        }

        @Test
        void shouldRequireResourceActions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING)));
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
        @Mock JWT token;
        @Mock JFRConnection conn;
        @Mock CryostatFlightRecorderService svc;

        @Test
        void shouldRespond404IfNotFound() throws Exception {
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(claims.getStringClaim(Mockito.anyString())).thenReturn(null);
            Mockito.when(token.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            new Answer<>() {
                                @Override
                                public Optional<InputStream> answer(InvocationOnMock args)
                                        throws Throwable {
                                    TargetConnectionManager.ConnectedTask ct =
                                            (TargetConnectionManager.ConnectedTask)
                                                    args.getArguments()[1];
                                    return (Optional<InputStream>) ct.execute(conn);
                                }
                            });
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of());
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespond500IfStreamCannotBeOpened() throws Exception {
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(claims.getStringClaim(Mockito.anyString())).thenReturn(null);
            Mockito.when(token.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            new Answer<>() {
                                @Override
                                public Optional<InputStream> answer(InvocationOnMock args)
                                        throws Throwable {
                                    TargetConnectionManager.ConnectedTask ct =
                                            (TargetConnectionManager.ConnectedTask)
                                                    args.getArguments()[1];
                                    return (Optional<InputStream>) ct.execute(conn);
                                }
                            });
            Mockito.when(conn.getService()).thenReturn(svc);
            IRecordingDescriptor desc = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(desc.getName()).thenReturn("myrecording");
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(desc));
            Mockito.when(svc.openStream(Mockito.any(), Mockito.eq(false)))
                    .thenThrow(new FlightRecorderException(""));
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }

        @Test
        void shouldRespond500IfIOExceptionThrownDuringSendFile() throws Exception {
            HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(claims.getStringClaim(Mockito.anyString())).thenReturn(null);
            Mockito.when(token.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            new Answer<>() {
                                @Override
                                public Optional<Document> answer(InvocationOnMock args)
                                        throws Throwable {
                                    TargetConnectionManager.ConnectedTask ct =
                                            (TargetConnectionManager.ConnectedTask)
                                                    args.getArguments()[1];
                                    return (Optional<Document>) ct.execute(conn);
                                }
                            });
            Mockito.when(conn.getService()).thenReturn(svc);
            IRecordingDescriptor desc = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(desc.getName()).thenReturn("myrecording");
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(desc));
            byte[] src = new byte[1024 * 1024];
            new Random(123456).nextBytes(src);
            InputStream stream = new ByteArrayInputStream(src);
            Mockito.when(svc.openStream(Mockito.any(), Mockito.eq(false))).thenReturn(stream);

            // **************Mocking specific to OutputToReadStream***************
            Context context = Mockito.mock(Context.class);
            Mockito.when(vertx.getOrCreateContext()).thenReturn(context);
            Mockito.doAnswer(
                            invocation -> {
                                Handler<Void> action = invocation.getArgument(0);
                                action.handle(null);
                                return null;
                            })
                    .when(context)
                    .runOnContext(Mockito.any(Handler.class));

            Mockito.when(targetConnectionManager.markConnectionInUse(Mockito.any()))
                    .thenReturn(false);
            // ********************************************************************

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, token));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
            MatcherAssert.assertThat(
                    ex.getCause().getCause().getMessage(),
                    Matchers.equalTo(
                            "Target connection unexpectedly closed while streaming recording"));
        }

        @Test
        void shouldSendFileIfFound() throws Exception {
            HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(ctx.pathParam("recordingName")).thenReturn("myrecording");
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(claims.getStringClaim(Mockito.anyString())).thenReturn(null);
            Mockito.when(token.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            new Answer<>() {
                                @Override
                                public Optional<Document> answer(InvocationOnMock args)
                                        throws Throwable {
                                    TargetConnectionManager.ConnectedTask ct =
                                            (TargetConnectionManager.ConnectedTask)
                                                    args.getArguments()[1];
                                    return (Optional<Document>) ct.execute(conn);
                                }
                            });
            Mockito.when(conn.getService()).thenReturn(svc);
            IRecordingDescriptor desc = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(desc.getName()).thenReturn("myrecording");
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(desc));
            byte[] src = new byte[1024 * 1024];
            new Random(123456).nextBytes(src);
            InputStream stream = new ByteArrayInputStream(src);
            Mockito.when(svc.openStream(Mockito.any(), Mockito.eq(false))).thenReturn(stream);

            // **************Mocking specific to OutputToReadStream***************
            Buffer dst = Buffer.buffer(1024 * 1024);
            Mockito.doAnswer(
                            invocation -> {
                                BufferImpl chunk = invocation.getArgument(0);
                                dst.appendBuffer(chunk);
                                return resp;
                            })
                    .when(resp)
                    .write(Mockito.any(BufferImpl.class), Mockito.any(Handler.class));

            Context context = Mockito.mock(Context.class);
            Mockito.when(vertx.getOrCreateContext()).thenReturn(context);
            Mockito.doAnswer(
                            invocation -> {
                                Handler<Void> action = invocation.getArgument(0);
                                action.handle(null);
                                return null;
                            })
                    .when(context)
                    .runOnContext(Mockito.any(Handler.class));

            Mockito.doAnswer(
                            invocation -> {
                                Handler<AsyncResult<Void>> handler = invocation.getArgument(0);
                                handler.handle(Future.succeededFuture());
                                return null;
                            })
                    .when(resp)
                    .end(Mockito.any(Handler.class));

            Mockito.when(targetConnectionManager.markConnectionInUse(Mockito.any()))
                    .thenReturn(true);
            // ********************************************************************

            handler.handleWithValidJwt(ctx, token);

            Assertions.assertArrayEquals(src, dst.getBytes());
            InOrder inOrder = Mockito.inOrder(resp);
            inOrder.verify(resp).setChunked(true);
            inOrder.verify(resp)
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
            inOrder.verify(resp).end(Mockito.any(Handler.class));
        }
    }
}
