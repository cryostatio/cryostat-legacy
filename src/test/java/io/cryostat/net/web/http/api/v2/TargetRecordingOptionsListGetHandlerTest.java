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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingOptionsListGetHandlerTest {

    TargetRecordingOptionsListGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock DiscoveryStorage storage;
    @Mock IFlightRecorderService service;
    @Mock JFRConnection connection;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingOptionsListGetHandler(
                        auth, credentialsManager, targetConnectionManager, storage, gson);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v2/targets/:targetId/recordingOptionsList"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldUseSecurityContextForTarget() throws Exception {
        String targetId = "fooHost:0";

        RequestParameters requestParams = Mockito.mock(RequestParameters.class);

        Map<String, String> pathParams = Map.of("targetId", targetId);
        Mockito.when(requestParams.getPathParams()).thenReturn(pathParams);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(requestParams.getHeaders()).thenReturn(headers);

        ServiceRef sr = Mockito.mock(ServiceRef.class);
        Mockito.when(storage.lookupServiceByTargetId(targetId)).thenReturn(Optional.of(sr));
        SecurityContext sc = Mockito.mock(SecurityContext.class);
        Mockito.when(auth.contextFor(sr)).thenReturn(sc);

        SecurityContext actual = handler.securityContext(requestParams);
        MatcherAssert.assertThat(actual, Matchers.sameInstance(sc));
        Mockito.verify(storage).lookupServiceByTargetId(targetId);
        Mockito.verify(auth).contextFor(sr);
    }

    @Test
    void shouldRespondWithRecordingOptionsList() throws Exception {
        ServiceRef sr = Mockito.mock(ServiceRef.class);
        Mockito.when(storage.lookupServiceByTargetId(Mockito.anyString()))
                .thenReturn(Optional.of(sr));
        Mockito.when(auth.contextFor(sr)).thenReturn(SecurityContext.DEFAULT);

        IOptionDescriptor<String> descriptor = mock(IOptionDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.getDescription()).thenReturn("Foo Option");
        when(descriptor.getDefault()).thenReturn("bar");
        Map<String, IOptionDescriptor<?>> options = Map.of("foo-option", descriptor);

        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenReturn(options);

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", "foo:9091"));
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        try {
            handler.handle(ctx);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resp).end(responseCaptor.capture());
        Map<String, Object> response =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<Map<String, Object>>() {}.getType());

        Map<String, Object> expected = new HashMap<>();
        Map<String, Object> meta = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        List<Object> result = new ArrayList<>();
        result.add(Map.of("name", "foo", "description", "Foo Option", "defaultValue", "bar"));

        expected.put("meta", meta);
        meta.put("status", "OK");
        meta.put("type", "application/json");
        expected.put("data", data);
        data.put("result", result);

        MatcherAssert.assertThat(response, Matchers.equalTo(expected));
    }
}
