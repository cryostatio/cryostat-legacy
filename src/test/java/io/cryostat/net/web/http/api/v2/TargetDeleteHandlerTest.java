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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.internal.CustomTargetPlatformClient;

import com.google.gson.Gson;
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
class TargetDeleteHandlerTest {

    TargetDeleteHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock CustomTargetPlatformClient customTargetPlatformClient;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetDeleteHandler(auth, credentialsManager, gson, customTargetPlatformClient);
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
    void shouldHaveDELETEMethod() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.DELETE));
    }

    @Test
    void shouldHaveTargetsPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/targets/:targetId"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.DELETE_TARGET)));
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
    void testSuccessfulRequest() throws IOException, URISyntaxException {
        Map<String, String> pathParams = new HashMap<>();
        RequestParameters requestParameters = Mockito.mock(RequestParameters.class);
        Mockito.when(requestParameters.getPathParams()).thenReturn(pathParams);
        Mockito.when(customTargetPlatformClient.removeTarget(Mockito.any(URI.class)))
                .thenReturn(true);

        String targetId = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        pathParams.put("targetId", targetId);

        IntermediateResponse<Void> response = handler.handle(requestParameters);
        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        Mockito.verify(customTargetPlatformClient).removeTarget(uriCaptor.capture());
        URI captured = uriCaptor.getValue();
        MatcherAssert.assertThat(captured, Matchers.equalTo(new URI(targetId)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"badUrl", "/some/path", ":8181/another", ":1234/with/a?query=param"})
    void testRequestWithBadTarget(String targetId) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("targetId", targetId);
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getPathParams()).thenReturn(pathParams);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void testRequestWithNonexistentTarget() throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getPathParams()).thenReturn(pathParams);
        String targetId = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";
        pathParams.put("targetId", targetId);
        Mockito.when(customTargetPlatformClient.removeTarget(Mockito.any(URI.class)))
                .thenReturn(false);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void testRequestWithIOException() throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        RequestParameters params = Mockito.mock(RequestParameters.class);
        Mockito.when(params.getPathParams()).thenReturn(pathParams);
        String targetId = "service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi";

        pathParams.put("targetId", targetId);

        Mockito.when(customTargetPlatformClient.removeTarget(Mockito.any(URI.class)))
                .thenThrow(IOException.class);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }
}
