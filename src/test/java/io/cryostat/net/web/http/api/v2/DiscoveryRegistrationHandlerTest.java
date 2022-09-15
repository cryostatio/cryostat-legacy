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

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

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
    AbstractV2RequestHandler<Map<String, String>> handler;
    @Mock AuthManager auth;
    @Mock DiscoveryStorage storage;
    @Mock WebServer webServer;
    @Mock DiscoveryJwtHelper jwt;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new DiscoveryRegistrationHandler(
                        auth, storage, () -> webServer, jwt, UUID::fromString, gson, logger);
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
        void shouldReturnJsonMimeType() {
            MatcherAssert.assertThat(handler.mimeType(), Matchers.equalTo(HttpMimeType.JSON));
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

            IntermediateResponse<Map<String, String>> resp = handler.handle(requestParams);

            MatcherAssert.assertThat(resp.getStatusCode(), Matchers.equalTo(201));
            MatcherAssert.assertThat(
                    resp.getHeaders(),
                    Matchers.equalTo(Map.of(HttpHeaders.LOCATION, "/api/v2.2/discovery/" + id)));
            MatcherAssert.assertThat(
                    resp.getBody(), Matchers.equalTo(Map.of("token", token, "id", id.toString())));

            Mockito.verify(storage)
                    .register(
                            Mockito.eq("test-realm"),
                            Mockito.eq(URI.create("http://example.com/callback")));
        }
    }
}
