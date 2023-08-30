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
package io.cryostat.net.web.http.api.v1;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.management.remote.JMXServiceURL;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetsGetHandlerTest {

    TargetsGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock DiscoveryStorage storage;
    @Mock Logger logger;
    @Mock JFRConnectionToolkit connectionToolkit;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new TargetsGetHandler(auth, credentialsManager, storage, gson, logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v1/targets"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldReturnListOfTargets() throws Exception {
        Mockito.when(connectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<JMXServiceURL>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        String.format("/jndi/rmi://%s:%s/jmxrmi", host, port));
                            }
                        });
        ServiceRef target =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(connectionToolkit.createServiceURL("foo", 1)),
                        "foo");

        List<ServiceRef> targets = Collections.singletonList(target);
        Mockito.when(storage.listDiscoverableServices()).thenReturn(List.of(target));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        handler.handleAuthenticated(ctx);

        Mockito.verifyNoMoreInteractions(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resp).end(responseCaptor.capture());
        Mockito.verifyNoMoreInteractions(resp);
        List<ServiceRef> result =
                gson.fromJson(
                        responseCaptor.getValue(), new TypeToken<List<ServiceRef>>() {}.getType());
        MatcherAssert.assertThat(result, Matchers.equalTo(targets));
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    }
}
