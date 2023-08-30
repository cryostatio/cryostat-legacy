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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.CredentialsGetHandler.Cred;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialsGetHandlerTest {

    CredentialsGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new CredentialsGetHandler(auth, credentialsManager, gson, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldBeAPIV2_2() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.2/credentials"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_CREDENTIALS)));
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

        @Test
        void shouldDelegateToCredentialsManager() throws Exception {
            Cred cred1 = new Cred();
            cred1.id = 1;
            cred1.matchExpression = "target.alias == \"foo\"";
            cred1.numMatchingTargets = 1;
            Cred cred2 = new Cred();
            cred1.id = 10;
            cred2.matchExpression = "target.alias == \"bar\"";
            cred2.numMatchingTargets = 2;
            Mockito.when(credentialsManager.getAll())
                    .thenReturn(
                            Map.of(
                                    cred1.id,
                                    cred1.matchExpression,
                                    cred2.id,
                                    cred2.matchExpression));
            ServiceRef mockTarget1 = Mockito.mock(ServiceRef.class);
            ServiceRef mockTarget2 = Mockito.mock(ServiceRef.class);
            Set<ServiceRef> set1 = new HashSet<>();
            set1.add(mockTarget1);
            Set<ServiceRef> set2 = new HashSet<>();
            set2.add(mockTarget1);
            set2.add(mockTarget2);
            Mockito.when(credentialsManager.resolveMatchingTargets(cred1.id)).thenReturn(set1);
            Mockito.when(credentialsManager.resolveMatchingTargets(cred2.id)).thenReturn(set2);

            IntermediateResponse<List<Cred>> response = handler.handle(requestParams);

            List<Cred> actual = response.getBody();
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    actual,
                    Matchers.containsInAnyOrder(Matchers.equalTo(cred1), Matchers.equalTo(cred2)));
            MatcherAssert.assertThat(actual, Matchers.hasSize(2));

            Mockito.verify(credentialsManager).getAll();
            Mockito.verify(credentialsManager, Mockito.times(2))
                    .resolveMatchingTargets(Mockito.anyInt());
        }
    }
}
