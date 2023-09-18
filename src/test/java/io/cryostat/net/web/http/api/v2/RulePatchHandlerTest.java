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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.MockVertx;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
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
class RulePatchHandlerTest {

    RulePatchHandler handler;
    Vertx vertx = MockVertx.vertx();
    @Mock AuthManager auth;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock DiscoveryStorage storage;
    @Mock CredentialsManager credentialsManager;
    @Mock RuleRegistry registry;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
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
                new RulePatchHandler(
                        vertx,
                        auth,
                        storage,
                        recordingTargetHelper,
                        credentialsManager,
                        registry,
                        notificationFactory,
                        gson,
                        logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeV2Handler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.PATCH));
        }

        @Test
        void shouldHaveExpectedApiPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/rules/:name"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.UPDATE_RULE)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldBeAsyncHandler() {
            Assertions.assertTrue(handler.isAsync());
        }

        @Test
        void shouldBeOrderedHandler() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Requests {
        @Mock RequestParameters params;
        final String testRuleName = "Test_Rule";

        @Test
        void shouldEnableRule() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));

            Mockito.when(params.getBody()).thenReturn("{\"enabled\" : true}");

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .enabled(false)
                            .build();

            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));

            IntermediateResponse<Void> response = handler.handle(params);

            Mockito.verify(registry).enableRule(rule, true);

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("RuleUpdated");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder).message(rule);
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(204));
        }

        @Test
        void shouldDisableRuleAndCleanup() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));

            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("clean", "true");
            Mockito.when(params.getQueryParams()).thenReturn(queryParams);
            Mockito.when(params.getBody()).thenReturn("{\"enabled\" : false}");

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .enabled(true)
                            .build();

            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));
            Mockito.when(registry.applies(Mockito.any(Rule.class), Mockito.any(ServiceRef.class)))
                    .thenReturn(true);

            ServiceRef serviceRef =
                    new ServiceRef(
                            "id",
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "io.cryostat.Cryostat");
            Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of(serviceRef));

            IntermediateResponse<Void> response = handler.handle(params);

            Mockito.verify(registry).enableRule(rule, false);

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("RuleUpdated");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder).message(rule);
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();

            Mockito.verify(vertx, Mockito.times(2)).executeBlocking(Mockito.any());
            Mockito.verify(registry)
                    .applies(Mockito.any(Rule.class), Mockito.any(ServiceRef.class));
            Mockito.verify(recordingTargetHelper)
                    .stopRecording(
                            Mockito.any(ConnectionDescriptor.class),
                            Mockito.eq(rule.getRecordingName()),
                            Mockito.eq(true));

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(204));
        }

        @Test
        void shouldRespondWith404ForNonexistentRule() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }
    }
}
