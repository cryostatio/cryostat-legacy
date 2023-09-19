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
package io.cryostat.net.web.http;

import static org.mockito.Mockito.when;

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
import io.cryostat.net.web.http.api.ApiVersion;

import io.fabric8.kubernetes.client.KubernetesClientException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractAuthenticatedRequestHandlerTest {

    RequestHandler handler;
    @Mock RoutingContext ctx;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler = new AuthenticatedHandler(auth, credentialsManager, logger);
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

    @Test
    void shouldPutDefaultContentTypeHeader() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        handler.handle(ctx);
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    }

    @Test
    void shouldThrow401IfAuthorizationFails() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(false));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @Test
    void shouldThrow403IfAuthenticationFails() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new PermissionDeniedException(
                                        "namespace", "resourc.group", "verb", "reason")));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(403));
    }

    @Test
    void shouldThrow401IfAuthenticationFails2() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new KubernetesClientException("test")));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @Test
    void shouldThrow403IfAuthorizationFails2() {
        // Check a doubly-nested PermissionDeniedException
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new ExecutionException(
                                        new PermissionDeniedException(
                                                "namespace", "resource.group", "verb", "reason"))));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(403));
    }

    @Test
    void shouldThrow401IfAuthenticationFails3() {
        // Check doubly-nested KubernetesClientException with its own cause
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new ExecutionException(
                                        new KubernetesClientException(
                                                "test", new Exception("test2")))));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @Test
    void shouldThrow500IfAuthThrows() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new NullPointerException()));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @Nested
    class WithHandlerThrownException {

        @BeforeEach
        void setup2() {
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldPropagateIfHandlerThrowsHttpException() {
            Exception expectedException = new HttpException(200);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex, Matchers.sameInstance(expectedException));
        }

        @Test
        void shouldThrow500IfConnectionFails() {
            Exception expectedException = new ConnectionException("");
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }

        @Test
        void shouldThrow427IfConnectionFailsDueToTargetAuth() {
            Exception cause = new SecurityException();
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            Mockito.when(ctx.response()).thenReturn(resp);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            Mockito.verify(resp).putHeader("X-JMX-Authenticate", "Basic");
        }

        @Test
        void shouldThrow502IfConnectionFailsDueToSslTrust() {
            Exception cause = new ConnectIOException("SSL trust");
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(502));
            MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("Target SSL Untrusted"));
        }

        @Test
        void shouldThrow404IfConnectionFailsDueToInvalidTarget() {
            Exception cause = new UnknownHostException("localhostt");
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
            MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("Target Not Found"));
        }

        @Test
        void shouldThrow500IfHandlerThrowsUnexpectedly() {
            Exception expectedException = new NullPointerException();
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, logger, expectedException);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }
    }

    @Nested
    class WithTargetAuth {

        ConnectionDescriptorHandler handler;
        @Mock HttpServerRequest req;
        @Mock MultiMap headers;

        @BeforeEach
        void setup3() {
            handler = new ConnectionDescriptorHandler(auth, credentialsManager, logger);
            Mockito.when(ctx.request()).thenReturn(req);
            when(req.headers()).thenReturn(headers);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldUseNoCredentialsWithoutAuthorizationHeader() {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(headers.contains(Mockito.anyString())).thenReturn(false);

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
        void shouldThrow427WithMalformedAuthorizationHeader(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(
                            headers.contains(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(true);
            Mockito.when(
                            req.getHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(authHeader);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getPayload(), Matchers.equalTo("Invalid X-JMX-Authorization format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Type credentials",
                    "Bearer credentials",
                })
        void shouldThrow427WithBadAuthorizationType(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(
                            headers.contains(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(true);
            Mockito.when(
                            req.getHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(authHeader);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getPayload(), Matchers.equalTo("Unacceptable X-JMX-Authorization type"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic bm9zZXBhcmF0b3I=", // credential value of "noseparator"
                    "Basic b25lOnR3bzp0aHJlZQ==", // credential value of "one:two:three"
                })
        void shouldThrow427WithBadCredentialFormat(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(
                            headers.contains(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(true);
            Mockito.when(
                            req.getHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(authHeader);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getPayload(),
                    Matchers.equalTo("Unrecognized X-JMX-Authorization credential format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic foo:bar",
                })
        void shouldThrow427WithUnencodedCredentials(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(ctx.response()).thenReturn(resp);
            Mockito.when(
                            headers.contains(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(true);
            Mockito.when(
                            req.getHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(authHeader);

            HttpException ex =
                    Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getPayload(),
                    Matchers.equalTo(
                            "X-JMX-Authorization credentials do not appear to be Base64-encoded"));
        }

        @Test
        void shouldIncludeCredentialsFromAppropriateHeader() {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParam("targetId")).thenReturn(targetId);
            Mockito.when(
                            headers.contains(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn(true);
            Mockito.when(
                            req.getHeader(
                                    AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER))
                    .thenReturn("Basic Zm9vOmJhcg==");

            Assertions.assertDoesNotThrow(() -> handler.handle(ctx));
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertTrue(desc.getCredentials().isPresent());
        }
    }

    static class AuthenticatedHandler extends AbstractAuthenticatedRequestHandler {
        AuthenticatedHandler(
                AuthManager auth, CredentialsManager credentialsManager, Logger logger) {
            super(auth, credentialsManager, logger);
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
        public void handleAuthenticated(RoutingContext ctx) throws Exception {}
    }

    static class ThrowingAuthenticatedHandler extends AuthenticatedHandler {
        private final Exception thrown;

        ThrowingAuthenticatedHandler(
                AuthManager auth,
                CredentialsManager credentialsManager,
                Logger logger,
                Exception thrown) {
            super(auth, credentialsManager, logger);
            this.thrown = thrown;
        }

        @Override
        public void handleAuthenticated(RoutingContext ctx) throws Exception {
            throw thrown;
        }
    }

    static class ConnectionDescriptorHandler extends AuthenticatedHandler {
        ConnectionDescriptor desc;

        ConnectionDescriptorHandler(
                AuthManager auth, CredentialsManager credentialsManager, Logger logger) {
            super(auth, credentialsManager, logger);
        }

        @Override
        public void handleAuthenticated(RoutingContext ctx) throws Exception {
            desc = getConnectionDescriptorFromContext(ctx);
        }
    }
}
