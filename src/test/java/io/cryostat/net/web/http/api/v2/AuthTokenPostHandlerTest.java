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

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
class AuthTokenPostHandlerTest {

    AuthTokenPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock AssetJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new AuthTokenPostHandler(
                        auth, credentialsManager, gson, jwt, () -> webServer, logger);
    }

    @Nested
    class ApiSpec {
        @Test
        void shouldBeV2_1Api() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_1));
        }

        @Test
        void shouldUseExpectedPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.1/auth/token"));
        }

        @Test
        void shouldUsePostVerb() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldRequireNoResourceActions() {
            MatcherAssert.assertThat(handler.resourceActions(), Matchers.equalTo(Set.of()));
        }

        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }
    }

    @Nested
    class Behaviour {
        @Mock RequestParameters params;

        @Test
        void shouldThrowIfNoResourceClaimMade() throws Exception {
            MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(attrs);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "not a url",
                    "https://cryostat.example.com:8080",
                    "https://cryostat.com:8080/api/v1/recordings/foo.jfr",
                    "http://cryostat.com/api/v1/recordings/foo.jfr",
                })
        void shouldThrowIfResourceClaimUrlIsInvalid(String resourceUrl) throws Exception {
            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);
            MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
            attrs.set("resource", resourceUrl);
            Mockito.when(params.getFormAttributes()).thenReturn(attrs);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        void shouldRespondWithToken() throws Exception {
            URL hostUrl = new URL("http://cryostat.example.com:8080");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);
            MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
            String resource = "/api/v1/recordings/foo.jfr";
            attrs.set("resource", resource);
            Mockito.when(params.getFormAttributes()).thenReturn(attrs);
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set("Authorization", "Basic user:pass");
            headers.set("X-JMX-Authorization", "Basic user2:pass2");
            Mockito.when(params.getHeaders()).thenReturn(headers);

            String token = "mytoken";
            Mockito.when(
                            jwt.createAssetDownloadJwt(
                                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(token);

            IntermediateResponse<Map<String, String>> resp = handler.handle(params);
            MatcherAssert.assertThat(resp.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    resp.getBody(),
                    Matchers.equalTo(
                            Map.of("resourceUrl", String.format("%s?token=%s", resource, token))));

            Mockito.verify(jwt)
                    .createAssetDownloadJwt(
                            "Basic user:pass", "/api/v1/recordings/foo.jfr", "Basic user2:pass2");
            Mockito.verifyNoMoreInteractions(jwt);
        }
    }
}
