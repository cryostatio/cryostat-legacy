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

import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.ConnectIOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.rjmx.ConnectionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.PermissionDeniedException;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.ApiVersion;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractAssetJwtConsumingHandlerTest {

    AbstractAssetJwtConsumingHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwtHelper;
    @Mock WebServer webServer;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new JwtConsumingHandler(
                        auth, credentialsManager, jwtHelper, () -> webServer, logger);
        Mockito.lenient().when(ctx.response()).thenReturn(resp);
        Mockito.lenient()
                .when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.lenient()
                .when(resp.putHeader(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(resp);
    }

    @Nested
    class InvalidJwt {

        MultiMap params;

        @BeforeEach
        void setup() {
            params = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(ctx.queryParams()).thenReturn(params);
        }

        @Test
        void shouldThrowIfJwtDoesntParse() throws Exception {
            params.set("token", "mytoken");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString()))
                    .thenThrow(new BadJWTException(""));
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrowIfResourceClaimUriInvalid() throws Exception {
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");

            params.set("token", "mytoken");
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource")).thenReturn("not-a-uri");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString())).thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrowIfResourceClaimUriDoesntMatch() throws Exception {
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");

            params.set("token", "mytoken");
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource"))
                    .thenReturn("http://othercryostat.com:8080/api/resource");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString())).thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }
    }

    @Nested
    class AuthManagerRejection {

        MultiMap params;

        @BeforeEach
        void setup() throws Exception {
            params = MultiMap.caseInsensitiveMultiMap();
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(ctx.queryParams()).thenReturn(params);
            Mockito.when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");

            params.set("token", "mytoken");
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource"))
                    .thenReturn("http://cryostat.example.com:8080/api/resource");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString())).thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);
        }

        @Test
        void shouldThrow401IfAuthFails() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(false));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails2() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new PermissionDeniedException(
                                            "namespace", "resource.group", "verb", "reason")));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails3() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new KubernetesClientException("test")));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails4() {
            // Check a doubly-nested PermissionDeniedException
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new ExecutionException(
                                            new PermissionDeniedException(
                                                    "namespace",
                                                    "resource.group",
                                                    "verb",
                                                    "reason"))));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails5() {
            // Check doubly-nested KubernetesClientException with its own cause
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new ExecutionException(
                                            new KubernetesClientException(
                                                    "test", new Exception("test2")))));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthThrows() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.failedFuture(new NullPointerException()));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }
    }

    @Nested
    class WithHandlerThrownException {

        MultiMap params;

        @BeforeEach
        void setup() throws Exception {
            params = MultiMap.caseInsensitiveMultiMap();
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(ctx.queryParams()).thenReturn(params);
            Mockito.when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");

            params.set("token", "mytoken");
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource"))
                    .thenReturn("http://cryostat.example.com:8080/api/resource");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString())).thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);

            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldPropagateIfHandlerThrowsApiException() {
            Exception expectedException = new ApiException(200);
            handler =
                    new ThrowingJwtConsumingHandler(
                            auth,
                            credentialsManager,
                            jwtHelper,
                            () -> webServer,
                            logger,
                            expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex, Matchers.sameInstance(expectedException));
        }

        @Test
        void shouldThrow427IfConnectionFailsDueToTargetAuth() {
            Exception cause = new SecurityException();
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingJwtConsumingHandler(
                            auth,
                            credentialsManager,
                            jwtHelper,
                            () -> webServer,
                            logger,
                            expectedException);

            Mockito.when(ctx.response()).thenReturn(resp);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            Mockito.verify(resp).putHeader("X-JMX-Authenticate", "Basic");
        }

        @Test
        void shouldThrow502IfConnectionFailsDueToSslTrust() {
            Exception cause = new ConnectIOException("SSL trust");
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingJwtConsumingHandler(
                            auth,
                            credentialsManager,
                            jwtHelper,
                            () -> webServer,
                            logger,
                            expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(502));
            MatcherAssert.assertThat(ex.getCause(), Matchers.instanceOf(ConnectionException.class));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("Target SSL Untrusted"));
        }

        @Test
        void shouldThrow404IfConnectionFailsDueToInvalidTarget() {
            Exception cause = new UnknownHostException("localhostt");
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingJwtConsumingHandler(
                            auth,
                            credentialsManager,
                            jwtHelper,
                            () -> webServer,
                            logger,
                            expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.equalTo("Target Not Found"));
        }

        @Test
        void shouldThrow500IfHandlerThrowsUnexpectedly() {
            Exception expectedException = new NullPointerException();
            handler =
                    new ThrowingJwtConsumingHandler(
                            auth,
                            credentialsManager,
                            jwtHelper,
                            () -> webServer,
                            logger,
                            expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }
    }

    @Nested
    class WithTargetAuth {

        ConnectionDescriptorHandler handler;
        @Mock HttpServerRequest req;
        MultiMap params;
        JWTClaimsSet claims;

        @BeforeEach
        void setup() throws Exception {
            params = MultiMap.caseInsensitiveMultiMap();
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.when(ctx.request()).thenReturn(req);
            Mockito.when(ctx.queryParams()).thenReturn(params);
            Mockito.when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");

            params.set("token", "mytoken");
            JWT jwt = Mockito.mock(JWT.class);
            claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource"))
                    .thenReturn("http://cryostat.example.com:8080/api/resource");
            Mockito.when(jwtHelper.parseAssetDownloadJwt(Mockito.anyString())).thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);

            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));

            handler =
                    new ConnectionDescriptorHandler(
                            auth, credentialsManager, jwtHelper, () -> webServer, logger);
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldUseNoCredentialsWithoutJmxAuthClaim() {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);

            handler.handle(ctx);
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertFalse(desc.getCredentials().isPresent());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "credentialsWithoutAuthType",
                })
        void shouldThrow427WithMalformedJmxAuthClaim(String authHeader) throws Exception {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(claims.getStringClaim("jmxauth")).thenReturn(authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("Invalid jmxauth claim format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Type credentials",
                    "Bearer credentials",
                })
        void shouldThrow427WithBadJmxAuthClaimType(String authHeader) throws Exception {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(claims.getStringClaim("jmxauth")).thenReturn(authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo("Unacceptable jmxauth credentials type"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic bm9zZXBhcmF0b3I=", // credential value of "noseparator"
                    "Basic b25lOnR3bzp0aHJlZQ==", // credential value of "one:two:three"
                })
        void shouldThrow427WithBadJmxAuthClaimCredentialFormat(String authHeader) throws Exception {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(claims.getStringClaim("jmxauth")).thenReturn(authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo("Unrecognized jmxauth claim credential format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic foo:bar",
                })
        void shouldThrow427WithUnencodedJmxAuthClaimCredentials(String authHeader)
                throws Exception {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(claims.getStringClaim("jmxauth")).thenReturn(authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo(
                            "jmxauth claim credentials do not appear to be Base64-encoded"));
        }

        @Test
        void shouldIncludeCredentialsFromAppropriateJmxAuthClaim() throws Exception {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(claims.getStringClaim("jmxauth")).thenReturn("Basic Zm9vOmJhcg==");

            Assertions.assertDoesNotThrow(() -> handler.handle(ctx));
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertTrue(desc.getCredentials().isPresent());
        }
    }

    static class JwtConsumingHandler extends AbstractAssetJwtConsumingHandler {
        JwtConsumingHandler(
                AuthManager auth,
                CredentialsManager credentialsManager,
                AssetJwtHelper jwtHelper,
                Lazy<WebServer> webServer,
                Logger logger) {
            super(auth, credentialsManager, jwtHelper, webServer, logger);
        }

        @Override
        public ApiVersion apiVersion() {
            return ApiVersion.V1;
        }

        @Override
        public String path() {
            return null;
        }

        @Override
        public HttpMethod httpMethod() {
            return null;
        }

        @Override
        public Set<ResourceAction> resourceActions() {
            return ResourceAction.NONE;
        }

        @Override
        public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {}
    }

    static class ThrowingJwtConsumingHandler extends JwtConsumingHandler {
        private final Exception thrown;

        ThrowingJwtConsumingHandler(
                AuthManager auth,
                CredentialsManager credentialsManager,
                AssetJwtHelper jwtHelper,
                Lazy<WebServer> webServer,
                Logger logger,
                Exception thrown) {
            super(auth, credentialsManager, jwtHelper, webServer, logger);
            this.thrown = thrown;
        }

        @Override
        public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
            throw thrown;
        }
    }

    static class ConnectionDescriptorHandler extends JwtConsumingHandler {
        ConnectionDescriptor desc;

        ConnectionDescriptorHandler(
                AuthManager auth,
                CredentialsManager credentialsManager,
                AssetJwtHelper jwtHelper,
                Lazy<WebServer> webServer,
                Logger logger) {
            super(auth, credentialsManager, jwtHelper, webServer, logger);
        }

        @Override
        public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
            desc = getConnectionDescriptorFromJwt(ctx, jwt);
        }
    }
}
