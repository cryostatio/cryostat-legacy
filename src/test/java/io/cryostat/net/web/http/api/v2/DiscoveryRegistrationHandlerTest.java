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
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.internal.PlatformDetectionStrategy;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryRegistrationHandlerTest {
    AbstractV2RequestHandler<Map<String, Object>> handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock DiscoveryStorage storage;
    @Mock WebServer webServer;
    @Mock DiscoveryJwtHelper jwt;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new DiscoveryRegistrationHandler(
                        auth,
                        credentialsManager,
                        env,
                        storage,
                        () -> webServer,
                        Set.of(new AllEnvPlatformStrategy(), new FakePlatformStrategy()),
                        jwt,
                        UUID::fromString,
                        gson,
                        logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldBe2_2APIVersion() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.2/discovery"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.CREATE_TARGET)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(true));
        }
    }

    @Nested
    class RequestHandling {

        @Mock RequestParameters requestParams;

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\n", "\t"})
        void shouldThrowIfRealmAttrBlank(String realm) throws Exception {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("callback", "some-url");
            if (realm != null) {
                attrs.put("realm", realm);
            }
            Mockito.when(requestParams.getBody()).thenReturn(gson.toJson(attrs));

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));

            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\n", "\t", "not a url"})
        void shouldThrowIfCallbackAttrBlankOrInvalid(String callback) throws Exception {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("realm", "test");
            if (callback != null) {
                attrs.put("callback", callback);
            }
            Mockito.when(requestParams.getBody()).thenReturn(gson.toJson(attrs));

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));

            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        void shouldRegisterWithStorageAndSendResponse() throws Exception {
            UUID id = UUID.randomUUID();
            Mockito.when(storage.register(Mockito.anyString(), Mockito.any(URI.class)))
                    .thenReturn(id);

            Mockito.when(env.getEnv()).thenReturn(Map.of("CRYOSTAT", "hello", "TEST", "true"));

            Mockito.when(requestParams.getBody())
                    .thenReturn(
                            gson.toJson(
                                    Map.of(
                                            "callback", "http://example.com/callback",
                                            "realm", "test-realm")));
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set("Authorization", "None");
            Mockito.when(requestParams.getHeaders()).thenReturn(headers);

            Mockito.when(webServer.getHostUrl()).thenReturn(new URL("http://localhost:8181/"));

            InetAddress addr = Mockito.mock(InetAddress.class);
            Mockito.when(requestParams.getAddress()).thenReturn(addr);

            String token = "abcd-1234";
            Mockito.when(
                            jwt.createDiscoveryPluginJwt(
                                    Mockito.any(String.class),
                                    Mockito.any(String.class),
                                    Mockito.any(InetAddress.class),
                                    Mockito.any(URI.class)))
                    .thenReturn(token);

            IntermediateResponse<Map<String, Object>> resp = handler.handle(requestParams);

            MatcherAssert.assertThat(resp.getStatusCode(), Matchers.equalTo(201));
            MatcherAssert.assertThat(
                    resp.getHeaders(),
                    Matchers.equalTo(Map.of(HttpHeaders.LOCATION, "/api/v2.2/discovery/" + id)));
            MatcherAssert.assertThat(
                    resp.getBody(),
                    Matchers.equalTo(
                            Map.of(
                                    "token",
                                    token,
                                    "id",
                                    id.toString(),
                                    "env",
                                    Map.of(
                                            "CRYOSTAT",
                                            "hello",
                                            "TEST",
                                            "true",
                                            "HELLO",
                                            "WORLD"))));

            Mockito.verify(storage)
                    .register(
                            Mockito.eq("test-realm"),
                            Mockito.eq(URI.create("http://example.com/callback")));
        }
    }

    static class AllEnvPlatformStrategy implements PlatformDetectionStrategy {
        @Override
        public Map<String, String> environment(Environment env) {
            return env.getEnv();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public PlatformClient getPlatformClient() {
            throw new UnsupportedOperationException("Unimplemented method 'getPlatformClient'");
        }

        @Override
        public AuthManager getAuthManager() {
            throw new UnsupportedOperationException("Unimplemented method 'getAuthManager'");
        }
    }

    static class FakePlatformStrategy implements PlatformDetectionStrategy {
        @Override
        public Map<String, String> environment(Environment env) {
            return Map.of("HELLO", "WORLD");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public PlatformClient getPlatformClient() {
            throw new UnsupportedOperationException("Unimplemented method 'getPlatformClient'");
        }

        @Override
        public AuthManager getAuthManager() {
            throw new UnsupportedOperationException("Unimplemented method 'getAuthManager'");
        }
    }
}
