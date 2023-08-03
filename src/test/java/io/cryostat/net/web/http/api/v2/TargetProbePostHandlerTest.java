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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.openjdk.jmc.rjmx.IConnectionHandle;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TargetProbePostHandlerTest {

    TargetProbePostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock LocalProbeTemplateService templateService;
    @Mock FileSystem fs;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Environment env;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);
        this.handler =
                new TargetProbePostHandler(
                        logger,
                        notificationFactory,
                        templateService,
                        fs,
                        auth,
                        credentialsManager,
                        targetConnectionManager,
                        env,
                        gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldBeV2API() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/v2/targets/:targetId/probes/:probeTemplate"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(), Matchers.equalTo(ResourceAction.NONE));
        }

        @Test
        void shouldProducePlaintext() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.PLAINTEXT)));
        }

        @Test
        void shouldRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(true));
        }
    }

    @Nested
    class Requests {

        @Mock RequestParameters requestParams;

        @Test
        public void shouldRespondOK() throws Exception {
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("targetId", "foo", "probeTemplate", "bar"));
            Mockito.when(requestParams.getHeaders()).thenReturn(MultiMap.caseInsensitiveMultiMap());
            JFRConnection connection = Mockito.mock(JFRConnection.class);
            IConnectionHandle handle = Mockito.mock(IConnectionHandle.class);
            MBeanServerConnection mbsc = Mockito.mock(MBeanServerConnection.class);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));
            String templateContent =
                    "<jfragent> <config> <classprefix>_test</classprefix>"
                        + " <allowtostring>true</allowtostring>"
                        + " <allowconverter>true</allowconverter> </config> <events> <event"
                        + " id=\"test\"> <label>Test</label> <description>TestEvent</description>"
                        + " <class>io.cryostat.net.web.http.api.v2.TargetProbePostHandlerTest</class>"
                        + " <path>test</path> <stacktrace>true</stacktrace>"
                        + " <rethrow>false</rethrow> <location>ENTRY</location> <method>"
                        + " <name>handleAuthenticated</name>"
                        + " <descriptor>(Lio/vertx/ext/web/RoutingContext;)V</descriptor> </method>"
                        + " </event> </events> </jfragent>";
            Mockito.when(templateService.getTemplateContent(Mockito.anyString()))
                    .thenReturn(templateContent);
            Mockito.when(connection.getHandle()).thenReturn(handle);
            Mockito.when(handle.getServiceOrDummy(MBeanServerConnection.class)).thenReturn(mbsc);
            Object result = Mockito.mock(Object.class);
            Mockito.when(
                            mbsc.invoke(
                                    any(ObjectName.class),
                                    any(String.class),
                                    any(Object[].class),
                                    any(String[].class)))
                    .thenReturn(result);
            IntermediateResponse<Void> response = handler.handle(requestParams);
            ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);

            Mockito.verify(notificationBuilder).message(argumentCaptor.capture());
            Map<String, Object> s = argumentCaptor.getValue();
            MatcherAssert.assertThat(s.get("probeTemplate"), Matchers.equalTo("bar"));
            MatcherAssert.assertThat(s.get("targetId"), Matchers.equalTo("foo"));
            MatcherAssert.assertThat(s.get("events"), Matchers.instanceOf(List.class));
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
        }

        @Test
        public void shouldRespond400WhenTargetIdIsMissing() throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", ""));
            try {
                IntermediateResponse<Void> response = handler.handle(requestParams);
            } catch (ApiException e) {
                MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(400));
            }
        }
    }
}
