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
import io.cryostat.net.web.http.api.ApiData;
import io.cryostat.net.web.http.api.ApiMeta;
import io.cryostat.net.web.http.api.ApiResponse;
import io.cryostat.net.web.http.api.ApiResultData;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import com.nimbusds.jwt.JWT;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryDeregistrationHandlerTest {
    AbstractDiscoveryJwtConsumingHandler<String> handler;
    @Mock AuthManager auth;
    @Mock DiscoveryJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock DiscoveryStorage storage;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new DiscoveryDeregistrationHandler(
                        auth, jwt, () -> webServer, storage, UUID::fromString, gson, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeDELETEHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
        }

        @Test
        void shouldBe2_2APIVersion() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.2/discovery/:id"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.DELETE_TARGET)));
        }
    }

    @Nested
    class RequestHandling {

        @Mock RoutingContext ctx;
        @Mock HttpServerResponse resp;
        @Mock JWT jwt;

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\n", "\t", "not a uuid", "1234", "abc-123"})
        void shouldThrowIfIdParamInvalid(String id) throws Exception {
            if (id != null) {
                Mockito.when(ctx.pathParam("id")).thenReturn(id);
            }

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, jwt));

            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        void shouldDeregisterWithStorageAndSendResponse() throws Exception {
            UUID uuid = UUID.randomUUID();
            Mockito.when(storage.deregister(Mockito.any(UUID.class))).thenReturn(null);

            Mockito.when(ctx.pathParam("id")).thenReturn(uuid.toString());

            handler.handleWithValidJwt(ctx, jwt);

            ArgumentCaptor<ApiResponse> respCaptor = ArgumentCaptor.forClass(ApiResponse.class);

            Mockito.verify(storage).deregister(Mockito.eq(uuid));
            Mockito.verify(ctx).json(respCaptor.capture());

            ApiResponse resp = respCaptor.getValue();
            MatcherAssert.assertThat(resp, Matchers.notNullValue());

            ApiMeta meta = resp.getMeta();
            MatcherAssert.assertThat(meta, Matchers.notNullValue());
            MatcherAssert.assertThat(meta.getStatus(), Matchers.equalTo("OK"));
            MatcherAssert.assertThat(meta.getMimeType(), Matchers.equalTo(HttpMimeType.JSON));

            ApiData data = resp.getData();
            MatcherAssert.assertThat(data, Matchers.isA(ApiResultData.class));
            MatcherAssert.assertThat(
                    ((ApiResultData) data).getResult(), Matchers.equalTo(uuid.toString()));
        }
    }
}
