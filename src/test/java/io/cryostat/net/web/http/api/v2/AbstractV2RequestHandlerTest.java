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

import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.rmi.ConnectIOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.script.ScriptException;

import org.openjdk.jmc.rjmx.ConnectionException;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
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
class AbstractV2RequestHandlerTest {

    RequestHandler handler;
    @Mock RoutingContext ctx;
    MultiMap headers;
    @Mock Map<String, String> pathParams;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        Mockito.lenient().when(ctx.pathParams()).thenReturn(pathParams);
        Mockito.lenient().when(ctx.queryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.lenient().when(ctx.fileUploads()).thenReturn(List.of());

        this.headers = MultiMap.caseInsensitiveMultiMap();

        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.lenient().when(req.headers()).thenReturn(headers);
        Mockito.lenient().when(ctx.request()).thenReturn(req);
        Mockito.lenient().when(ctx.response()).thenReturn(resp);
        Mockito.lenient().when(ctx.body()).thenReturn(Mockito.mock(RequestBody.class));

        this.handler = new AuthenticatedHandler(auth, credentialsManager, gson);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(handler.resourceActions(), Matchers.equalTo(Set.of()));
    }

    @Test
    void shouldThrow401IfAuthFails() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(false));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @Test
    void shouldThrow500IfAuthThrows() {
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new NullPointerException()));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldSendRawResponseForNonJsonPlaintextMimetype() {
        AbstractV2RequestHandler<String> handler =
                new RawResponseHandler(auth, credentialsManager, gson);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(200);
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/jfc+xml");
        Mockito.verify(resp).end("<xml></xml>");
    }

    @Test
    void shouldSendFileResponseIfHandlerProvidesFileLocation() {
        AbstractV2RequestHandler<Path> handler =
                new FileResponseHandler(auth, credentialsManager, gson);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(200);
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
        Mockito.verify(resp).sendFile("/my/file.html");
    }

    @Nested
    class WithHandlerThrownException {

        @BeforeEach
        void setup2() {
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldPropagateIfHandlerThrowsApiException() {
            Exception expectedException = new ApiException(200);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex, Matchers.sameInstance(expectedException));
        }

        @Test
        void shouldThrow500IfConnectionFails() {
            Exception expectedException = new ConnectionException("");
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }

        @Test
        void shouldThrow427IfConnectionFailsDueToTargetAuth() {
            Exception cause = new SecurityException();
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

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
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(502));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("Target SSL Untrusted"));
        }

        @Test
        void shouldThrow404IfConnectionFailsDueToInvalidTarget() {
            Exception cause = new UnknownHostException("localhostt");
            Exception expectedException = new ConnectionException("");
            expectedException.initCause(cause);
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.equalTo("Target Not Found"));
        }

        @Test
        void shouldThrow500IfHandlerThrowsUnexpectedly() {
            Exception expectedException = new NullPointerException();
            handler =
                    new ThrowingAuthenticatedHandler(
                            auth, credentialsManager, gson, expectedException);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        }
    }

    @Nested
    class WithTargetAuth {

        ConnectionDescriptorHandler handler;

        @BeforeEach
        void setup3() {
            handler = new ConnectionDescriptorHandler(auth, credentialsManager, gson);
            when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                    .thenReturn(CompletableFuture.completedFuture(true));
        }

        @Test
        void shouldIncludeContentTypeHeader() {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));

            handler.handle(ctx);

            Mockito.verify(resp)
                    .putHeader(HttpHeaders.CONTENT_TYPE, handler.produces().get(0).mime());
        }

        @Test
        void shouldUseNoCredentialsIfNoneStoredOrProvided() throws ScriptException {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));

            handler.handle(ctx);
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertFalse(desc.getCredentials().isPresent());

            Mockito.verify(credentialsManager).getCredentialsByTargetId(targetId);
        }

        @Test
        void shouldUseStoredCredentials() throws ScriptException {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));

            Credentials creds = new Credentials("a user", "thepass");
            Mockito.when(credentialsManager.getCredentialsByTargetId(targetId)).thenReturn(creds);

            handler.handle(ctx);
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertTrue(desc.getCredentials().isPresent());
            MatcherAssert.assertThat(desc.getCredentials().get(), Matchers.equalTo(creds));

            Mockito.verify(credentialsManager).getCredentialsByTargetId(targetId);
        }

        @Test
        void shouldOverrideStoredCredentialsByAuthorizationHeader() throws ScriptException {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(
                    "X-JMX-Authorization",
                    "Basic " + Base64.getEncoder().encodeToString("jmxuser:jmxpass".getBytes()));
            Mockito.when(ctx.request().headers()).thenReturn(headers);

            handler.handle(ctx);
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertTrue(desc.getCredentials().isPresent());
            MatcherAssert.assertThat(
                    desc.getCredentials().get(),
                    Matchers.equalTo(new Credentials("jmxuser", "jmxpass")));

            Mockito.verifyNoInteractions(credentialsManager);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "credentialsWithoutAuthType",
                })
        void shouldThrow427WithMalformedAuthorizationHeader(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            headers.set(AbstractV2RequestHandler.JMX_AUTHORIZATION_HEADER, authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("Invalid X-JMX-Authorization format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Type credentials",
                    "Bearer credentials",
                })
        void shouldThrow427WithBadAuthorizationType(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            headers.set(AbstractV2RequestHandler.JMX_AUTHORIZATION_HEADER, authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            ex.printStackTrace();
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo("Unacceptable X-JMX-Authorization type"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic bm9zZXBhcmF0b3I=", // credential value of "noseparator"
                    "Basic b25lOnR3bzp0aHJlZQ==", // credential value of "one:two:three"
                })
        void shouldThrow427WithBadCredentialFormat(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            headers.set(AbstractV2RequestHandler.JMX_AUTHORIZATION_HEADER, authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo("Unrecognized X-JMX-Authorization credential format"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Basic foo:bar",
                })
        void shouldThrow427WithUnencodedCredentials(String authHeader) {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            headers.set(AbstractV2RequestHandler.JMX_AUTHORIZATION_HEADER, authHeader);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo(
                            "X-JMX-Authorization credentials do not appear to be Base64-encoded"));
        }

        @Test
        void shouldIncludeCredentialsFromAppropriateHeader() {
            String targetId = "fooTarget";
            Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", targetId));
            headers.set(AbstractV2RequestHandler.JMX_AUTHORIZATION_HEADER, "Basic Zm9vOmJhcg==");

            Assertions.assertDoesNotThrow(() -> handler.handle(ctx));
            ConnectionDescriptor desc = handler.desc;

            MatcherAssert.assertThat(desc.getTargetId(), Matchers.equalTo(targetId));
            Assertions.assertTrue(desc.getCredentials().isPresent());
        }
    }

    static class AuthenticatedHandler extends AbstractV2RequestHandler<String> {
        AuthenticatedHandler(AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
            super(auth, credentialsManager, gson);
        }

        @Override
        public ApiVersion apiVersion() {
            return ApiVersion.V2;
        }

        @Override
        public boolean requiresAuthentication() {
            return true;
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
            return Set.of();
        }

        @Override
        public List<HttpMimeType> produces() {
            return List.of(HttpMimeType.PLAINTEXT);
        }

        @Override
        public IntermediateResponse<String> handle(RequestParameters params) throws Exception {
            return new IntermediateResponse<String>().body("OK");
        }
    }

    static class ThrowingAuthenticatedHandler extends AuthenticatedHandler {
        private final Exception thrown;

        ThrowingAuthenticatedHandler(
                AuthManager auth,
                CredentialsManager credentialsManager,
                Gson gson,
                Exception thrown) {
            super(auth, credentialsManager, gson);
            this.thrown = thrown;
        }

        @Override
        public IntermediateResponse<String> handle(RequestParameters params) throws Exception {
            throw thrown;
        }
    }

    static class ConnectionDescriptorHandler extends AuthenticatedHandler {
        ConnectionDescriptor desc;

        ConnectionDescriptorHandler(
                AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
            super(auth, credentialsManager, gson);
        }

        @Override
        public IntermediateResponse<String> handle(RequestParameters params) throws Exception {
            desc = getConnectionDescriptorFromParams(params);
            return new IntermediateResponse<String>().body("");
        }
    }

    static class FileResponseHandler extends AbstractV2RequestHandler<Path> {
        FileResponseHandler(AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
            super(auth, credentialsManager, gson);
        }

        @Override
        public ApiVersion apiVersion() {
            return ApiVersion.V2;
        }

        @Override
        public boolean requiresAuthentication() {
            return false;
        }

        @Override
        public String path() {
            return "/my/path";
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public Set<ResourceAction> resourceActions() {
            return Set.of();
        }

        @Override
        public List<HttpMimeType> produces() {
            return List.of(HttpMimeType.HTML);
        }

        @Override
        public IntermediateResponse<Path> handle(RequestParameters params) throws Exception {
            return new IntermediateResponse<Path>().body(Path.of("/my/file.html"));
        }
    }

    static class RawResponseHandler extends AbstractV2RequestHandler<String> {
        RawResponseHandler(AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
            super(auth, credentialsManager, gson);
        }

        @Override
        public ApiVersion apiVersion() {
            return ApiVersion.V2;
        }

        @Override
        public boolean requiresAuthentication() {
            return false;
        }

        @Override
        public String path() {
            return "/another/path";
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public Set<ResourceAction> resourceActions() {
            return Set.of();
        }

        @Override
        public List<HttpMimeType> produces() {
            return List.of(HttpMimeType.JFC);
        }

        @Override
        public IntermediateResponse<String> handle(RequestParameters params) throws Exception {
            return new IntermediateResponse<String>().body("<xml></xml>");
        }
    }
}
