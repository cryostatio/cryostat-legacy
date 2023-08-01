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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetEventsGetHandlerTest {

    TargetEventsGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Logger logger;
    @Mock CryostatFlightRecorderService service;
    @Mock JFRConnection connection;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetEventsGetHandler(auth, credentialsManager, targetConnectionManager, gson);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v2/targets/:targetId/events"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_TARGET)));
    }

    @Test
    void shouldHandleNoMatches() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn(Collections.emptyList());

        RequestParameters params =
                new RequestParameters(
                        "application/json",
                        InetAddress.getByName("127.0.01"),
                        Map.of("targetId", "foo:9091"),
                        MultiMap.caseInsensitiveMultiMap().set("q", "foo"),
                        MultiMap.caseInsensitiveMultiMap(),
                        MultiMap.caseInsensitiveMultiMap(),
                        Set.of(),
                        null);

        IntermediateResponse<List<SerializableEventTypeInfo>> result = handler.handle(params);

        MatcherAssert.assertThat(result.getBody(), Matchers.equalTo(Collections.emptyList()));
    }

    @Test
    void shouldHandleMatches() throws Exception {
        IEventTypeInfo infoA = mock(IEventTypeInfo.class);
        IEventTypeID eventIdA = mock(IEventTypeID.class);
        when(eventIdA.getFullKey()).thenReturn("com.example.A");
        when(infoA.getEventTypeID()).thenReturn(eventIdA);
        when(infoA.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoA.getDescription()).thenReturn("Does some fooing");

        IEventTypeInfo infoB = mock(IEventTypeInfo.class);
        IEventTypeID eventIdB = mock(IEventTypeID.class);
        when(eventIdB.getFullKey()).thenReturn("com.example.B");
        when(infoB.getEventTypeID()).thenReturn(eventIdB);
        when(infoB.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoB.getName()).thenReturn("FooProperty");

        IEventTypeInfo infoC = mock(IEventTypeInfo.class);
        IEventTypeID eventIdC = mock(IEventTypeID.class);
        when(eventIdC.getFullKey()).thenReturn("com.example.C");
        when(infoC.getEventTypeID()).thenReturn(eventIdC);
        when(infoC.getHierarchicalCategory()).thenReturn(new String[] {"com", "example", "Foo"});

        IEventTypeInfo infoD = mock(IEventTypeInfo.class);
        IEventTypeID eventIdD = mock(IEventTypeID.class);
        when(eventIdD.getFullKey()).thenReturn("com.example.Foo");
        when(infoD.getEventTypeID()).thenReturn(eventIdD);
        when(infoD.getHierarchicalCategory()).thenReturn(new String[0]);

        IEventTypeInfo infoE = mock(IEventTypeInfo.class);
        IEventTypeID eventIdE = mock(IEventTypeID.class);
        when(eventIdE.getFullKey()).thenReturn("com.example.E");
        when(infoE.getEventTypeID()).thenReturn(eventIdE);
        when(infoE.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoE.getName()).thenReturn("bar");
        when(infoE.getDescription()).thenReturn("Does some baring");

        List<IEventTypeInfo> events = Arrays.asList(infoA, infoB, infoC, infoD, infoE);

        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> {
                            try {
                                return ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection);
                            } catch (Exception e) {
                                e.printStackTrace();
                                throw e;
                            }
                        });
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn((List) events);

        RequestParameters params =
                new RequestParameters(
                        "application/json",
                        InetAddress.getByName("127.0.0.1"),
                        Map.of("targetId", "foo:9091"),
                        MultiMap.caseInsensitiveMultiMap().set("q", "foo"),
                        MultiMap.caseInsensitiveMultiMap(),
                        MultiMap.caseInsensitiveMultiMap(),
                        Set.of(),
                        null);
        IntermediateResponse<List<SerializableEventTypeInfo>> result = handler.handle(params);

        MatcherAssert.assertThat(
                result.getBody(),
                Matchers.equalTo(
                        Arrays.asList(
                                new SerializableEventTypeInfo(infoA),
                                new SerializableEventTypeInfo(infoB),
                                new SerializableEventTypeInfo(infoC),
                                new SerializableEventTypeInfo(infoD))));
    }
}
