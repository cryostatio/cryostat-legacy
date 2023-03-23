/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TargetProbeDeleteHandlerTest {

    TargetProbeDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock FileSystem fs;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock DiscoveryStorage storage;
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
                new TargetProbeDeleteHandler(
                        logger,
                        notificationFactory,
                        fs,
                        auth,
                        credentialsManager,
                        storage,
                        targetConnectionManager,
                        env,
                        gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeDELETEHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
        }

        @Test
        void shouldBeBetaAPI() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/v2/targets/:targetId/probes"));
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
        private static final String AGENT_OBJECT_NAME =
                "org.openjdk.jmc.jfr.agent:type=AgentController";
        private static final String DEFINE_EVENT_PROBES = "defineEventProbes";

        @Test
        public void shouldRespondOK() throws Exception {
            Mockito.when(requestParams.getPathParams()).thenReturn(Map.of("targetId", "foo"));
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
            Mockito.when(connection.getHandle()).thenReturn(handle);
            Mockito.when(handle.getServiceOrDummy(MBeanServerConnection.class)).thenReturn(mbsc);
            Mockito.when(
                            mbsc.invoke(
                                    any(ObjectName.class),
                                    any(String.class),
                                    any(Object[].class),
                                    any(String[].class)))
                    .thenReturn(null);
            IntermediateResponse<Void> response = handler.handle(requestParams);
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
