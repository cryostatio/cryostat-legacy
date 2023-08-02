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

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

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
public class MBeanMetricsGetHandlerTest {
    MBeanMetricsGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock Gson gson;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new MBeanMetricsGetHandler(
                        authManager, credentialsManager, gson, targetConnectionManager, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(true));
        }

        @Test
        void shouldBeAPIV2_3() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2_3));
        }

        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(ResourceAction.READ_TARGET, ResourceAction.READ_CREDENTIALS)));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/v2.3/targets/:targetId/mbeanMetrics"));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }
    }

    @Nested
    class RequestHandling {
        @Mock ConnectionDescriptor connectionDescriptor;
        @Mock JFRConnection connection;
        @Mock RequestParameters requestParams;
        @Mock MBeanMetrics metrics;

        @Test
        void shouldReturnMetrics() throws Exception {
            when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "foo"));
            when(requestParams.getHeaders()).thenReturn(MultiMap.caseInsensitiveMultiMap());
            when(targetConnectionManager.executeConnectedTask(
                            Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            when(connection.getMBeanMetrics()).thenReturn(metrics);

            IntermediateResponse<MBeanMetrics> response = handler.handle(requestParams);

            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(metrics));
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
        }
    }
}
