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

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.beta.CredentialTestPostHandler.CredentialTestResult;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

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
class CredentialTestPostHandlerTest {
    CredentialTestPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    @Mock TargetConnectionManager tcm;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new CredentialTestPostHandler(auth, credentialsManager, gson, tcm);
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
                    handler.path(), Matchers.equalTo("/api/beta/credentials/:targetId"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
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
        @Mock JFRConnection connection;
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        String username = "user";
        String password = "pass";
        String targetId = "targetId";

        @BeforeEach
        void setup() {
            this.form.set("username", username);
            this.form.set("password", password);
        }

        @Test
        void shouldRespondNA() throws Exception {
            when(requestParams.getFormAttributes()).thenReturn(form);
            when(requestParams.getPathParams()).thenReturn(Map.of("targetId", targetId));

            when(tcm.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            IntermediateResponse<CredentialTestResult> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(CredentialTestResult.NA));
        }

        @Test
        void shouldRespondSUCCESS() throws Exception {
            when(requestParams.getFormAttributes()).thenReturn(form);
            when(requestParams.getPathParams()).thenReturn(Map.of("targetId", targetId));

            when(tcm.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenThrow(
                            new Exception(
                                    new SecurityException("first failure without credentials")))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            IntermediateResponse<CredentialTestResult> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    response.getBody(), Matchers.equalTo(CredentialTestResult.SUCCESS));
        }

        @Test
        void shouldRespondFAILURE() throws Exception {
            when(requestParams.getFormAttributes()).thenReturn(form);
            when(requestParams.getPathParams()).thenReturn(Map.of("targetId", targetId));

            when(tcm.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenThrow(
                            new Exception(
                                    new SecurityException("first failure without credentials")))
                    .thenThrow(
                            new Exception(
                                    new SecurityException("second failure with credentials")));

            IntermediateResponse<CredentialTestResult> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    response.getBody(), Matchers.equalTo(CredentialTestResult.FAILURE));
        }
    }
}
