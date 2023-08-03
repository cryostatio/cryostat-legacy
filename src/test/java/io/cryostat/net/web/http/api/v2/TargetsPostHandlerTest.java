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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.nashorn.internal.runtime.ParserException;

@ExtendWith(MockitoExtension.class)
class TargetsPostHandlerTest {

    TargetsPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock DiscoveryStorage storage;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock CustomTargetPlatformClient customTargetPlatformClient;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification.Builder notificationBuilder;
    @Mock Notification notification;
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
                new TargetsPostHandler(
                        auth,
                        credentialsManager,
                        gson,
                        storage,
                        jvmIdHelper,
                        customTargetPlatformClient,
                        notificationFactory,
                        logger);
    }

    @Test
    void shouldRequireAuthentication() {
        Assertions.assertTrue(handler.requiresAuthentication());
    }

    @Test
    void shouldBeV2API() {
        MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
    }

    @Test
    void shouldHavePOSTMethod() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHaveTargetsPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/targets"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.CREATE_TARGET)));
    }

    @Test
    void shouldProduceJson() {
        MatcherAssert.assertThat(handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
    }

    @Test
    void shouldNotBeAsync() {
        Assertions.assertFalse(handler.isAsync());
    }

    @Test
    void shouldBeOrdered() {
        Assertions.assertTrue(handler.isOrdered());
    }

    @Test
    void testSuccessfulRequest() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(true);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        ArgumentCaptor<ServiceRef> refCaptor = ArgumentCaptor.forClass(ServiceRef.class);
        Mockito.verify(customTargetPlatformClient).addTarget(refCaptor.capture());
        ServiceRef captured = refCaptor.getValue();
        MatcherAssert.assertThat(captured.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(captured.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(captured.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                captured.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
        MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(captured));
    }

    @Test
    void testSuccessfulRequestWithCredentials() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(
                        credentialsManager.addCredentials(
                                Mockito.anyString(), Mockito.any(Credentials.class)))
                .thenReturn(1001);
        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(true);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        String username = "username";
        String password = "password";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("username", username);
        attrs.set("password", password);

        queries.set("storeCredentials", String.valueOf(true));

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        ArgumentCaptor<ServiceRef> refCaptor = ArgumentCaptor.forClass(ServiceRef.class);
        Mockito.verify(customTargetPlatformClient).addTarget(refCaptor.capture());
        ServiceRef captured = refCaptor.getValue();
        MatcherAssert.assertThat(captured.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(captured.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(captured.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                captured.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
        MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(captured));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"badUrl", "bad:123", "http://example.com/foo"})
    void testRequestWithBadTarget(String connectUrl) throws IOException {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);

        attrs.set("connectUrl", connectUrl);
        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testRequestWithBadAlias(String alias) throws IOException {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void testRequestWithDuplicateTarget() throws IOException {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");
        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", "TestTarget");

        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(false);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void testRequestWithAdditionalAnnotations() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(true);
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");
        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("annotations.cryostat.HOST", "app.example.com");
        attrs.set("annotations.cryostat.PID", "1234");
        attrs.set("annotations.cryostat.MADEUPKEY", "should not appear");

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        ArgumentCaptor<ServiceRef> refCaptor = ArgumentCaptor.forClass(ServiceRef.class);
        Mockito.verify(customTargetPlatformClient).addTarget(refCaptor.capture());
        ServiceRef captured = refCaptor.getValue();
        MatcherAssert.assertThat(captured.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(captured.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(captured.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                captured.getCryostatAnnotations(),
                Matchers.equalTo(
                        Map.of(
                                AnnotationKey.HOST,
                                "app.example.com",
                                AnnotationKey.PID,
                                "1234",
                                AnnotationKey.REALM,
                                "Custom Targets")));
        MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(captured));
    }

    @Test
    void testRequestWithIOException() throws IOException {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");
        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", "TestTarget");

        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any()))
                .thenThrow(IOException.class);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void testRequestWithDryRunQuery(String dryRunStr) throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);

        queries.set("dryrun", dryRunStr);

        Mockito.when(storage.contains(Mockito.any())).thenReturn(true);

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(202));

        ServiceRef respRef = response.getBody();
        MatcherAssert.assertThat(respRef.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(respRef.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(respRef.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                respRef.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "asdfg", "false", "FALSE", "False"})
    void testRequestWithBadOrFalseDryRunQuery(String dryRunStr) throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(true);
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);

        queries.set("dryrun", dryRunStr);

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        ArgumentCaptor<ServiceRef> refCaptor = ArgumentCaptor.forClass(ServiceRef.class);
        Mockito.verify(customTargetPlatformClient).addTarget(refCaptor.capture());
        ServiceRef captured = refCaptor.getValue();
        MatcherAssert.assertThat(captured.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(captured.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(captured.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                captured.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
        MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(captured));
    }

    @Test
    void testRequestWithDryRunQueryIncludingCredentials() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        String username = "username";
        String password = "password";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("username", username);
        attrs.set("password", password);

        queries.set("dryrun", "true");

        Mockito.when(storage.contains(Mockito.any())).thenReturn(true);

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(202));

        ServiceRef respRef = response.getBody();
        MatcherAssert.assertThat(respRef.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(respRef.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(respRef.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                respRef.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
    }

    @Test
    void testRequestWithStoreCredentialsQuery() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());
        Mockito.when(customTargetPlatformClient.addTarget(Mockito.any())).thenReturn(true);
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenReturn("id");

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        String username = "username";
        String password = "password";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("username", username);
        attrs.set("password", password);

        queries.set("storeCredentials", "true");

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        Mockito.verify(credentialsManager).addCredentials(Mockito.any(), Mockito.any());
        ServiceRef respRef = response.getBody();
        MatcherAssert.assertThat(respRef.getServiceUri(), Matchers.equalTo(new URI(connectUrl)));
        MatcherAssert.assertThat(respRef.getAlias(), Matchers.equalTo(Optional.of(alias)));
        MatcherAssert.assertThat(respRef.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
        MatcherAssert.assertThat(
                respRef.getCryostatAnnotations(),
                Matchers.equalTo(Map.of(AnnotationKey.REALM, "Custom Targets")));
    }

    @Test
    void testRequestWithJvmIdGetException() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        String username = "username";
        String password = "password";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("username", username);
        attrs.set("password", password);

        queries.set("dryrun", String.valueOf(true));

        Exception cause = new SecurityException();
        Exception jvmIdGetException = new JvmIdHelper.JvmIdGetException(cause, connectUrl);
        Mockito.when(
                        jvmIdHelper.getJvmId(
                                Mockito.anyString(),
                                Mockito.anyBoolean(),
                                Mockito.any(Optional.class)))
                .thenThrow(jvmIdGetException);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(427));
    }

    @Test
    void testRequestWithMatchExpressionValidationException() throws Exception {
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        MultiMap queries = MultiMap.caseInsensitiveMultiMap();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getFormAttributes()).thenReturn(attrs);
        Mockito.when(params.getQueryParams()).thenReturn(queries);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of());

        String connectUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        String alias = "TestTarget";
        String username = "username";
        String password = "password";

        attrs.set("connectUrl", connectUrl);
        attrs.set("alias", alias);
        attrs.set("username", username);
        attrs.set("password", password);

        queries.set("storeCredentials", String.valueOf(true));

        Exception cause = new ParserException("Parse failed");
        Exception matchExpressionValidationException =
                new MatchExpressionValidationException(cause);
        Mockito.when(
                        credentialsManager.addCredentials(
                                Mockito.anyString(), Mockito.any(Credentials.class)))
                .thenThrow(matchExpressionValidationException);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }
}
