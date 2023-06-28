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
package io.cryostat.net.web.http.api.v1;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.jmc.serialization.SerializableEventTypeInfo;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetEventsGetHandlerTest {

    TargetEventsGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager connectionManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetEventsGetHandler(
                        auth, credentialsManager, connectionManager, gson, logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/targets/:targetId/events"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldRespondWithErrorIfExceptionThrown() throws Exception {
        Mockito.when(
                        connectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenThrow(new Exception("dummy exception"));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("foo:9091");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        Assertions.assertThrows(Exception.class, () -> handler.handleAuthenticated(ctx));
    }

    @Test
    void shouldRespondWithEventsList() throws Exception {
        JFRConnection connection = Mockito.mock(JFRConnection.class);
        CryostatFlightRecorderService service = Mockito.mock(CryostatFlightRecorderService.class);

        IEventTypeInfo event1 = Mockito.mock(IEventTypeInfo.class);
        IEventTypeID eventTypeId1 = Mockito.mock(IEventTypeID.class);
        Mockito.when(eventTypeId1.getFullKey()).thenReturn("com.example.foo");
        Mockito.when(event1.getName()).thenReturn("foo");
        Mockito.when(event1.getEventTypeID()).thenReturn(eventTypeId1);
        Mockito.when(event1.getDescription()).thenReturn("Foo description");
        Mockito.when(event1.getHierarchicalCategory()).thenReturn(new String[] {"com", "example"});
        Mockito.when(event1.getOptionDescriptors()).thenReturn(Collections.emptyMap());

        IEventTypeInfo event2 = Mockito.mock(IEventTypeInfo.class);
        IEventTypeID eventTypeId2 = Mockito.mock(IEventTypeID.class);
        Mockito.when(eventTypeId2.getFullKey()).thenReturn("com.example.bar");
        Mockito.when(event2.getName()).thenReturn("bar");
        Mockito.when(event2.getEventTypeID()).thenReturn(eventTypeId2);
        Mockito.when(event2.getDescription()).thenReturn("Bar description");
        Mockito.when(event2.getHierarchicalCategory()).thenReturn(new String[] {"com", "example"});
        Mockito.when(event2.getOptionDescriptors()).thenReturn(Collections.emptyMap());

        Collection events = Arrays.asList(event1, event2);

        Mockito.when(
                        connectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableEventTypes()).thenReturn(events);

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("foo:9091");
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        handler.handleAuthenticated(ctx);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resp).end(responseCaptor.capture());
        List<SerializableEventTypeInfo> result =
                gson.fromJson(
                        responseCaptor.getValue(),
                        new TypeToken<List<SerializableEventTypeInfo>>() {}.getType());

        MatcherAssert.assertThat(
                result,
                Matchers.equalTo(
                        Arrays.asList(
                                new SerializableEventTypeInfo(event1),
                                new SerializableEventTypeInfo(event2))));
    }
}
