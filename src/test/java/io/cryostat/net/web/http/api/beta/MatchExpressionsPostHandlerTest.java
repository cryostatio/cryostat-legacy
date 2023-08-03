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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionManager;
import io.cryostat.rules.MatchExpressionManager.MatchedMatchExpression;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
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
class MatchExpressionsPostHandlerTest {
    MatchExpressionsPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock MatchExpressionManager expressionManager;
    @Mock MatchExpressionEvaluator expressionEvaluator;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification.Builder notificationBuilder;
    @Mock Notification notification;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.meta(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.message(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient().when(notificationBuilder.build()).thenReturn(notification);
        this.handler =
                new MatchExpressionsPostHandler(
                        auth,
                        credentialsManager,
                        expressionManager,
                        expressionEvaluator,
                        notificationFactory,
                        gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldBeAPIBeta() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/beta/matchExpressions"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.CREATE_MATCH_EXPRESSION)));
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
            Optional<MatchExpression> opt = Mockito.mock(Optional.class);
            MatchExpression expr = Mockito.mock(MatchExpression.class);
            int id = 10;
            Mockito.when(requestParams.getBody())
                    .thenReturn("{\"matchExpression\": \"target.alias == 'foo'\"}");
            String matchExpression = "target.alias == 'foo'";
            Mockito.when(expressionManager.addMatchExpression(Mockito.anyString())).thenReturn(id);
            Mockito.when(expressionManager.get(id)).thenReturn(opt);
            Mockito.when(opt.get()).thenReturn(expr);
            Mockito.when(expr.getMatchExpression()).thenReturn(matchExpression);

            IntermediateResponse<MatchedMatchExpression> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(201));
            Map<CharSequence, CharSequence> headers = response.getHeaders();
            MatcherAssert.assertThat(headers, Matchers.aMapWithSize(1));
            MatcherAssert.assertThat(
                    headers.get(HttpHeaders.LOCATION),
                    Matchers.equalTo("/api/beta/matchExpressions/" + id));

            Mockito.verify(expressionManager).addMatchExpression(matchExpression);
        }

        @Test
        void shouldDryRunWithMatchedTargets() throws Exception {
            String matchExpression = "target.alias == 'foo'";

            Mockito.when(requestParams.getBody())
                    .thenReturn(
                            "{\"matchExpression\": \"target.alias == 'foo'\", \"targets\":"
                                + " [{\"alias\":\"foo\",\"connectUrl\":\"service:jmx:rmi:///jndi/rmi://localhost:9091/jmxrmi\"}]}");

            ServiceRef target = Mockito.mock(ServiceRef.class);

            Mockito.when(
                            expressionManager.resolveMatchingTargets(
                                    Mockito.anyString(), Mockito.any()))
                    .thenReturn(Set.of(target));

            IntermediateResponse<MatchedMatchExpression> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatchedMatchExpression body = response.getBody();
            MatchedMatchExpression expected =
                    new MatchedMatchExpression(matchExpression, Set.of(target));
            MatcherAssert.assertThat(body, Matchers.equalTo(expected));

            Mockito.verify(expressionManager, Mockito.never())
                    .addMatchExpression(Mockito.anyString());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"invalid", "==", "", " "})
        void shouldRespond400IfMatchExpressionInvalid(String matchExpression) throws Exception {
            Mockito.when(requestParams.getBody())
                    .thenReturn(String.format("{\"matchExpression\": %s", matchExpression));
            Mockito.lenient()
                    .when(expressionManager.addMatchExpression(Mockito.anyString()))
                    .thenThrow(MatchExpressionValidationException.class);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));

            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }
    }
}
