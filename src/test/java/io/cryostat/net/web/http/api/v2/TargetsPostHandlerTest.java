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

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.recordings.JvmIdHelper;

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

@ExtendWith(MockitoExtension.class)
class TargetsPostHandlerTest {

    TargetsPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock DiscoveryStorage storage;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock CustomTargetPlatformClient customTargetPlatformClient;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetsPostHandler(
                        auth,
                        credentialsManager,
                        gson,
                        storage,
                        jvmIdHelper,
                        customTargetPlatformClient,
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

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

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

        IntermediateResponse<ServiceRef> response = handler.handle(params);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

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
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(406));
    }
}
