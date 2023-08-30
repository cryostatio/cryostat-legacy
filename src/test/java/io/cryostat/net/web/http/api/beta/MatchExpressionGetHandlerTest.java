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
package io.cryostat.net.web.http.api.beta;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionManager;
import io.cryostat.rules.MatchExpressionManager.MatchedMatchExpression;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
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
class MatchExpressionGetHandlerTest {
    MatchExpressionGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock MatchExpressionManager expressionManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new MatchExpressionGetHandler(auth, credentialsManager, expressionManager, gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldBeAPIBeta() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/beta/matchExpressions/:id"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_MATCH_EXPRESSION)));
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
        void shouldDelegateToMatchExpressionManager() throws Exception {
            String matchExpression = "target.alias == \"foo\"";
            ServiceRef serviceRef =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "foo");
            MatchExpression expr = Mockito.mock(MatchExpression.class);
            Mockito.when(expr.getMatchExpression()).thenReturn(matchExpression);
            MultiMap queryMap = MultiMap.caseInsensitiveMultiMap();
            queryMap.add("matches", "true");
            Mockito.when(requestParams.getQueryParams()).thenReturn(queryMap);
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("id", "10"));
            Mockito.when(expressionManager.get(10)).thenReturn(Optional.of(expr));
            Set<ServiceRef> targets = Set.of(serviceRef);
            Mockito.when(expressionManager.resolveMatchingTargets(expr)).thenReturn(targets);

            IntermediateResponse<MatchedMatchExpression> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    response.getBody(),
                    Matchers.equalTo(new MatchedMatchExpression(expr, targets)));

            Mockito.verify(expressionManager).get(10);
            Mockito.verify(expressionManager).resolveMatchingTargets(expr);
        }

        @Test
        void shouldRespond404IfIdUnknown() throws Exception {
            Mockito.when(expressionManager.get(Mockito.anyInt())).thenReturn(Optional.empty());
            MultiMap queryMap = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(requestParams.getQueryParams()).thenReturn(queryMap);
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("id", "10"));

            IntermediateResponse<?> resp = handler.handle(requestParams);

            MatcherAssert.assertThat(resp.getStatusCode(), Matchers.equalTo(404));
        }
    }
}
