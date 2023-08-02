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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryGetHandlerTest {

    AbstractV2RequestHandler<EnvironmentNode> handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock DiscoveryStorage storage;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new DiscoveryGetHandler(auth, credentialsManager, storage, gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeV2Handler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_1));
        }

        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldHaveExpectedApiPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.1/discovery"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
        }

        @Test
        void shouldProducePlaintext() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldNotBeAsyncHandler() {
            Assertions.assertFalse(handler.isAsync());
        }

        @Test
        void shouldBeOrderedHandler() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Requests {
        @Mock RequestParameters params;
        EnvironmentNode expected;

        @BeforeEach
        void setup() throws Exception {
            EnvironmentNode pod = new EnvironmentNode("appPod-1", KubernetesNodeType.POD);

            ServiceRef serviceRef =
                    new ServiceRef(
                            "id",
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "appReplica-1-1");
            TargetNode endpoint = new TargetNode(KubernetesNodeType.ENDPOINT, serviceRef);

            EnvironmentNode deployment =
                    new EnvironmentNode(
                            "appDeployment",
                            KubernetesNodeType.DEPLOYMENT,
                            Collections.emptyMap(),
                            Set.of(pod, endpoint));

            EnvironmentNode project =
                    new EnvironmentNode(
                            "myProject",
                            KubernetesNodeType.NAMESPACE,
                            Collections.emptyMap(),
                            Set.of(deployment));

            expected = project;
        }

        @Test
        void shouldRespondWithEnvironmentNode() throws Exception {
            Mockito.when(storage.getDiscoveryTree()).thenReturn(expected);

            IntermediateResponse<EnvironmentNode> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

            EnvironmentNode actual = response.getBody();

            MatcherAssert.assertThat(actual, Matchers.equalTo(expected));
            Mockito.verify(storage).getDiscoveryTree();
            Mockito.verifyNoMoreInteractions(storage);
        }
    }
}
