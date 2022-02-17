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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.FlightRecorderException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;

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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleDeleteHandlerTest {

    RuleDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock RuleRegistry registry;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock PlatformClient platformClient;
    @Mock CredentialsManager credentialsManager;
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
                new RuleDeleteHandler(
                        auth,
                        registry,
                        targetConnectionManager,
                        platformClient,
                        credentialsManager,
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
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
        }

        @Test
        void shouldHaveExpectedApiPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/rules/:name"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.DELETE_RULE)));
        }

        @Test
        void shouldHavePlaintextMimeType() {
            MatcherAssert.assertThat(handler.mimeType(), Matchers.equalTo(HttpMimeType.PLAINTEXT));
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
        void shouldRespondOk() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            Mockito.when(params.getQueryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .build();
            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));

            IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> response =
                    handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("RuleDeleted");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder).message(rule);
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();
        }

        @Test
        void shouldRespondWith404ForNonexistentRule() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldRespondWith500ForCleanupFailures() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("clean", "true");
            Mockito.when(params.getQueryParams()).thenReturn(queryParams);

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .build();
            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));
            Mockito.when(registry.applies(Mockito.any(), Mockito.any())).thenReturn(true);

            ServiceRef serviceRef =
                    new ServiceRef(
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "io.cryostat.Cryostat");
            Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));

            FlightRecorderException exception =
                    new FlightRecorderException(new Exception("test message"));
            Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenThrow(exception);

            IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> response =
                    handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(500));

            RuleDeleteHandler.CleanupFailure failure = new RuleDeleteHandler.CleanupFailure();
            failure.ref = serviceRef;
            failure.message = exception.getMessage();
            List<RuleDeleteHandler.CleanupFailure> actualList = response.getBody();
            MatcherAssert.assertThat(actualList, Matchers.hasSize(1));
            RuleDeleteHandler.CleanupFailure actual = actualList.get(0);
            MatcherAssert.assertThat(actual.ref, Matchers.sameInstance(serviceRef));
            MatcherAssert.assertThat(actual.message, Matchers.equalTo(exception.getMessage()));
        }

        @Test
        void shouldRespondWith200AfterCleanupNoop() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("clean", "true");
            Mockito.when(params.getQueryParams()).thenReturn(queryParams);

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .build();
            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));
            Mockito.when(registry.applies(Mockito.any(), Mockito.any())).thenReturn(true);

            ServiceRef serviceRef =
                    new ServiceRef(
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "io.cryostat.Cryostat");
            Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));

            JFRConnection connection = Mockito.mock(JFRConnection.class);
            Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            IFlightRecorderService service = Mockito.mock(IFlightRecorderService.class);
            Mockito.when(connection.getService()).thenReturn(service);

            Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

            IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> response =
                    handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
        }

        @Test
        void shouldRespondWith200AfterSuccessfulCleanup() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("clean", "true");
            Mockito.when(params.getQueryParams()).thenReturn(queryParams);

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .build();
            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));
            Mockito.when(registry.applies(Mockito.any(), Mockito.any())).thenReturn(true);

            ServiceRef serviceRef =
                    new ServiceRef(
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "io.cryostat.Cryostat");
            Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));

            JFRConnection connection = Mockito.mock(JFRConnection.class);
            Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            IFlightRecorderService service = Mockito.mock(IFlightRecorderService.class);
            Mockito.when(connection.getService()).thenReturn(service);

            IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));
            Mockito.when(recording.getName()).thenReturn(rule.getRecordingName());

            IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> response =
                    handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

            Mockito.verify(service).stop(recording);
            Mockito.verify(service, Mockito.never()).close(recording);
        }

        @Test
        void shouldRespondWith500AfterUnsuccessfulCleanup() throws Exception {
            Mockito.when(params.getPathParams()).thenReturn(Map.of("name", testRuleName));
            MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
            queryParams.set("clean", "true");
            Mockito.when(params.getQueryParams()).thenReturn(queryParams);

            Rule rule =
                    new Rule.Builder()
                            .name(testRuleName)
                            .matchExpression("true")
                            .eventSpecifier("template=Continuous")
                            .build();
            Mockito.when(registry.hasRuleByName(testRuleName)).thenReturn(true);
            Mockito.when(registry.getRule(testRuleName)).thenReturn(Optional.of(rule));
            Mockito.when(registry.applies(Mockito.any(), Mockito.any())).thenReturn(true);

            ServiceRef serviceRef =
                    new ServiceRef(
                            new URI("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                            "io.cryostat.Cryostat");
            Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));

            JFRConnection connection = Mockito.mock(JFRConnection.class);
            Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            IFlightRecorderService service = Mockito.mock(IFlightRecorderService.class);
            Mockito.when(connection.getService()).thenReturn(service);

            org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException exception =
                    new org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException("test message");
            Mockito.doThrow(exception).when(service).stop(Mockito.any());

            IRecordingDescriptor recording = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(recording));
            Mockito.when(recording.getName()).thenReturn(rule.getRecordingName());

            IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> response =
                    handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(500));

            RuleDeleteHandler.CleanupFailure failure = new RuleDeleteHandler.CleanupFailure();
            failure.ref = serviceRef;
            failure.message = exception.getMessage();
            List<RuleDeleteHandler.CleanupFailure> actualList = response.getBody();
            MatcherAssert.assertThat(actualList, Matchers.hasSize(1));
            RuleDeleteHandler.CleanupFailure actual = actualList.get(0);
            MatcherAssert.assertThat(actual.ref, Matchers.sameInstance(serviceRef));
            MatcherAssert.assertThat(actual.message, Matchers.equalTo(exception.getMessage()));

            Mockito.verify(service).stop(recording);
            Mockito.verify(service, Mockito.never()).close(recording);
        }
    }
}
