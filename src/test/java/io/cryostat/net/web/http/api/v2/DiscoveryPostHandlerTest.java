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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;

import com.google.gson.Gson;
import com.nimbusds.jwt.JWT;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
class DiscoveryPostHandlerTest {
    AbstractDiscoveryJwtConsumingHandler<Void> handler;
    @Mock AuthManager auth;
    @Mock DiscoveryJwtHelper jwt;
    @Mock WebServer webServer;
    @Mock DiscoveryStorage storage;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new DiscoveryPostHandler(
                        auth, jwt, () -> webServer, storage, UUID::fromString, gson, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeDELETEHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
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
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.CREATE_TARGET,
                                    ResourceAction.UPDATE_TARGET,
                                    ResourceAction.DELETE_TARGET)));
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

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(
                strings = {
                    "not json",
                    " some, values ",
                    "\"foo\":\"bar\"",
                })
        void shouldThrowIfBodyJsonInvalid(String json) throws Exception {
            UUID uuid = UUID.randomUUID();
            Mockito.when(ctx.pathParam("id")).thenReturn(uuid.toString());
            RequestBody body = Mockito.mock(RequestBody.class);
            Mockito.when(ctx.body()).thenReturn(body);
            Mockito.when(body.asString()).thenReturn(json);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handleWithValidJwt(ctx, jwt));

            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        @Disabled(
                "Test harness gson instance cannot deserialize AbstractNode - needs to have the"
                        + " various adapters registered")
        void shouldUpdateStorageAndSendResponse() throws Exception {
            UUID uuid = UUID.randomUUID();
            Mockito.when(storage.deregister(Mockito.any(UUID.class))).thenReturn(null);

            Set<AbstractNode> children = new HashSet<>();
            EnvironmentNode pod = new EnvironmentNode("TestEnvironment", KubernetesNodeType.POD);
            ServiceRef serviceRef =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://localhost/jmxrmi"),
                            "selftest");
            TargetNode leaf = new TargetNode(KubernetesNodeType.ENDPOINT, serviceRef);
            pod.addChildNode(leaf);
            children.add(pod);

            Mockito.when(ctx.pathParam("id")).thenReturn(uuid.toString());
            Mockito.when(ctx.getBodyAsString()).thenReturn(gson.toJson(children));

            handler.handleWithValidJwt(ctx, jwt);

            Mockito.verify(resp).end(Mockito.anyString());

            ArgumentCaptor<Set<AbstractNode>> captor = ArgumentCaptor.forClass(Set.class);

            Mockito.verify(storage).update(Mockito.eq(uuid), captor.capture());

            Set<AbstractNode> update = captor.getValue();

            MatcherAssert.assertThat(update, Matchers.equalTo(children));
        }
    }
}
