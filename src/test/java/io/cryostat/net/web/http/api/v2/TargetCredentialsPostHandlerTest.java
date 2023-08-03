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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
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
class TargetCredentialsPostHandlerTest {

    AbstractV2RequestHandler<Void> handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
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
                new TargetCredentialsPostHandler(
                        auth, credentialsManager, notificationFactory, gson, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldBeAPIV2() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/v2/targets/:targetId/credentials"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.CREATE_CREDENTIALS)));
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
        void shouldRespond400WhenUsernameOmitted() throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "fooTarget"));

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("password", "abc123");
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("\"username\" is required."));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void shouldRespond400WhenUsernameBlank(String username) throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "fooTarget"));

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("username", username);
            form.set("password", "abc123");
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("\"username\" is required."));
        }

        @Test
        void shouldRespond400WhenPasswordOmitted() throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "fooTarget"));

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("username", "adminuser");
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("\"password\" is required."));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void shouldRespond400WhenPasswordBlank(String password) throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "fooTarget"));

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("username", "adminuser");
            form.set("password", password);
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(
                    ex.getFailureReason(), Matchers.equalTo("\"password\" is required."));
        }

        @Test
        void shouldRespond400WhenFormEmpty() throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "fooTarget"));

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(
                    ex.getFailureReason(),
                    Matchers.equalTo("\"username\" is required. \"password\" is required."));
        }

        @Test
        void shouldDelegateToCredentialsManager() throws Exception {
            String targetId = "fooTarget";
            String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);
            String username = "adminuser";
            String password = "abc123";
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", targetId));
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("username", username);
            form.set("password", password);
            Mockito.when(requestParams.getFormAttributes()).thenReturn(form);

            IntermediateResponse<Void> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.nullValue());
            Mockito.verify(credentialsManager)
                    .addCredentials(matchExpression, new Credentials(username, password));

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("TargetCredentialsStored");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder).message(Map.of("target", matchExpression));
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();
        }
    }
}
