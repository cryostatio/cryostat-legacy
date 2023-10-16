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

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.discovery.PluginInfo;
import io.cryostat.net.AuthManager;
import io.cryostat.net.PermissionDeniedException;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.ApiVersion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractDiscoveryJwtConsumingHandlerTest {

    AbstractDiscoveryJwtConsumingHandler<String> handler;
    @Mock DiscoveryStorage storage;
    @Mock AuthManager auth;
    @Mock DiscoveryJwtHelper jwtHelper;
    @Mock WebServer webServer;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler = new JwtConsumingHandler(storage, auth, jwtHelper, () -> webServer, logger);
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

        MultiMap headers;
        MultiMap queryParams;
        UUID id;
        @Mock HttpServerRequest req;
        @Mock PluginInfo pluginInfo;

        @BeforeEach
        void setup()
                throws MalformedURLException,
                        SocketException,
                        UnknownHostException,
                        URISyntaxException {
            headers = MultiMap.caseInsensitiveMultiMap();
            queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("token", "mytoken");
            id = UUID.randomUUID();
            Mockito.lenient().when(ctx.queryParams()).thenReturn(queryParams);
            Mockito.lenient().when(ctx.pathParam("id")).thenReturn(id.toString());
            Mockito.lenient().when(ctx.request()).thenReturn(req);
            Mockito.lenient().when(req.headers()).thenReturn(headers);
            Mockito.lenient()
                    .when(req.absoluteURI())
                    .thenReturn("http://cryostat.example.com:8080/api/resource");
            Mockito.lenient()
                    .when(req.remoteAddress())
                    .thenReturn(SocketAddress.inetSocketAddress(8181, "localhost"));
            Mockito.lenient()
                    .when(webServer.getHostUrl())
                    .thenReturn(new URL("http://localhost:8181/"));
            Mockito.lenient().when(storage.getById(id)).thenReturn(Optional.of(pluginInfo));
            Mockito.lenient().when(pluginInfo.getRealm()).thenReturn("test-realm");
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldThrow401IfTokenNotProvided(String token) throws Exception {
            queryParams.set("token", token);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow404IfIdNotFound() throws Exception {
            Mockito.when(storage.getById(id)).thenReturn(Optional.empty());
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldThrow401IfJwtDoesntParse() throws Exception {
            Mockito.when(
                            jwtHelper.parseDiscoveryPluginJwt(
                                    Mockito.anyString(),
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.anyBoolean()))
                    .thenThrow(new BadJWTException(""));
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrowIfResourceClaimUriInvalid() throws Exception {
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource")).thenReturn("not-a-uri");
            Mockito.when(
                            jwtHelper.parseDiscoveryPluginJwt(
                                    Mockito.anyString(),
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.anyBoolean()))
                    .thenReturn(jwt);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrowIfResourceClaimUriDoesntMatch() throws Exception {
            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.when(claims.getStringClaim("resource"))
                    .thenReturn("http://othercryostat.com:8080/api/resource");
            Mockito.when(
                            jwtHelper.parseDiscoveryPluginJwt(
                                    Mockito.anyString(),
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.anyBoolean()))
                    .thenReturn(jwt);

            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }
    }

    @Nested
    class AuthManagerRejection {

        MultiMap headers;
        MultiMap queryParams;
        UUID id;
        @Mock HttpServerRequest req;
        @Mock PluginInfo pluginInfo;

        @BeforeEach
        void setup()
                throws MalformedURLException,
                        SocketException,
                        UnknownHostException,
                        URISyntaxException,
                        ParseException,
                        JOSEException,
                        BadJWTException {
            headers = MultiMap.caseInsensitiveMultiMap();
            queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("token", "mytoken");
            id = UUID.randomUUID();
            Mockito.lenient().when(ctx.queryParams()).thenReturn(queryParams);
            Mockito.lenient().when(ctx.pathParam("id")).thenReturn(id.toString());
            Mockito.lenient().when(ctx.request()).thenReturn(req);
            Mockito.lenient().when(req.headers()).thenReturn(headers);
            Mockito.lenient()
                    .when(req.absoluteURI())
                    .thenReturn("http://localhost:8181/api/v2.2/discovery/" + id.toString());
            Mockito.lenient()
                    .when(req.remoteAddress())
                    .thenReturn(SocketAddress.inetSocketAddress(8181, "localhost"));
            Mockito.lenient()
                    .when(webServer.getHostUrl())
                    .thenReturn(new URL("http://localhost:8181/"));
            Mockito.lenient().when(storage.getById(id)).thenReturn(Optional.of(pluginInfo));
            Mockito.lenient().when(pluginInfo.getRealm()).thenReturn("test-realm");

            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.lenient().when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.lenient()
                    .when(claims.getStringClaim("resource"))
                    .thenReturn("http://localhost:8181/api/v2.2/discovery/" + id.toString());
            Mockito.lenient()
                    .when(
                            jwtHelper.parseDiscoveryPluginJwt(
                                    Mockito.anyString(),
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.anyBoolean()))
                    .thenReturn(jwt);
        }

        @Test
        void shouldThrow401IfAuthFails() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(false));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails2() {
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(new KubernetesClientException("test")));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }

        @Test
        void shouldThrow401IfAuthFails4() {
            // Check a doubly-nested PermissionDeniedException
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
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
            Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.failedFuture(new NullPointerException()));

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
        }
    }

    @Nested
    class WithHandlerThrownException {

        MultiMap params;
        MultiMap headers;
        UUID id;
        @Mock PluginInfo pluginInfo;

        @BeforeEach
        void setup() throws Exception {
            id = UUID.randomUUID();
            headers = MultiMap.caseInsensitiveMultiMap();
            params = MultiMap.caseInsensitiveMultiMap();
            HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
            Mockito.lenient().when(ctx.request()).thenReturn(req);
            Mockito.lenient()
                    .when(req.remoteAddress())
                    .thenReturn(SocketAddress.inetSocketAddress(8181, "localhost"));
            Mockito.lenient().when(ctx.queryParams()).thenReturn(params);
            Mockito.lenient().when(req.headers()).thenReturn(headers);
            Mockito.lenient().when(ctx.pathParam("id")).thenReturn(UUID.randomUUID().toString());
            Mockito.lenient()
                    .when(req.absoluteURI())
                    .thenReturn("http://localhost:8181/api/v2.2/discovery/" + id.toString());

            params.set("token", "mytoken");
            Mockito.lenient()
                    .when(storage.getById(Mockito.any()))
                    .thenReturn(Optional.of(pluginInfo));
            Mockito.lenient().when(pluginInfo.getRealm()).thenReturn("test-realm");

            JWT jwt = Mockito.mock(JWT.class);
            JWTClaimsSet claims = Mockito.mock(JWTClaimsSet.class);
            Mockito.lenient().when(jwt.getJWTClaimsSet()).thenReturn(claims);
            Mockito.lenient()
                    .when(claims.getStringClaim("resource"))
                    .thenReturn("http://localhost:8181/api/v2.2/discovery/" + id.toString());
            Mockito.when(
                            jwtHelper.parseDiscoveryPluginJwt(
                                    Mockito.anyString(),
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.anyBoolean()))
                    .thenReturn(jwt);

            URL hostUrl = new URL("http://localhost:8181/api/v2.2/discovery/" + id.toString());
            Mockito.lenient().when(webServer.getHostUrl()).thenReturn(hostUrl);

            Mockito.lenient()
                    .when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldPropagateIfHandlerThrowsApiException() {
            Exception expectedException = new ApiException(200);
            handler =
                    new ThrowingJwtConsumingHandler(
                            storage, auth, jwtHelper, () -> webServer, logger, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex, Matchers.sameInstance(expectedException));
        }

        @Test
        void shouldThrow500IfHandlerThrowsUnexpectedly() {
            Exception expectedException = new NullPointerException();
            handler =
                    new ThrowingJwtConsumingHandler(
                            storage, auth, jwtHelper, () -> webServer, logger, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }
    }

    static class JwtConsumingHandler extends AbstractDiscoveryJwtConsumingHandler<String> {
        JwtConsumingHandler(
                DiscoveryStorage storage,
                AuthManager auth,
                DiscoveryJwtHelper jwtHelper,
                Lazy<WebServer> webServer,
                Logger logger) {
            super(storage, auth, jwtHelper, webServer, UUID::fromString, logger);
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
                DiscoveryStorage storage,
                AuthManager auth,
                DiscoveryJwtHelper jwtHelper,
                Lazy<WebServer> webServer,
                Logger logger,
                Exception thrown) {
            super(storage, auth, jwtHelper, webServer, logger);
            this.thrown = thrown;
        }

        @Override
        public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
            throw thrown;
        }
    }
}
