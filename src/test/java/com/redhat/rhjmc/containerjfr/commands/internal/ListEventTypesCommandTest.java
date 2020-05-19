/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableEventTypeInfo;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class ListEventTypesCommandTest implements ValidatesTargetId {

    ListEventTypesCommand command;
    @Mock ClientWriter cw;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    @Override
    public Command commandForValidationTesting() {
        return command;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(TARGET_ID);
    }

    @BeforeEach
    void setup() {
        command = new ListEventTypesCommand(cw, targetConnectionManager);
    }

    @Test
    void shouldBeNamedListEventTypes() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-event-types"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldNotValidateWrongArgc(int argc) {
        assertFalse(command.validate(new String[argc]));
        verify(cw).println("Expected one argument: hostname:port, ip:port, or JMX service URL");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPrintEventTypes() throws Exception {
        Collection eventTypes = Arrays.asList(createEvent("foo"), createEvent("bar"));

        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn(eventTypes);

        command.execute(new String[] {"fooHost:9091"});
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available event types:");
        inOrder.verify(cw).println("\tmocked toString: foo");
        inOrder.verify(cw).println("\tmocked toString: bar");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnListOutput() throws Exception {
        IEventTypeID eventTypeId = mock(IEventTypeID.class);
        when(eventTypeId.getFullKey()).thenReturn("com.example.foo");
        IEventTypeInfo eventInfo = mock(IEventTypeInfo.class);
        when(eventInfo.getName()).thenReturn("foo");
        when(eventInfo.getEventTypeID()).thenReturn(eventTypeId);
        when(eventInfo.getDescription()).thenReturn("Foo description");
        when(eventInfo.getHierarchicalCategory()).thenReturn(new String[] {"com", "example"});
        when(eventInfo.getOptionDescriptors()).thenReturn(Collections.emptyMap());

        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes())
                .thenReturn((Collection) Collections.singleton(eventInfo));

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"fooHost:9091"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(
                out.getPayload(),
                Matchers.equalTo(
                        Collections.singletonList(new SerializableEventTypeInfo(eventInfo))));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenThrow(FlightRecorderException.class);

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"fooHost:9091"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }

    private static IEventTypeInfo createEvent(String name) {
        IEventTypeInfo info = mock(IEventTypeInfo.class);
        when(info.toString()).thenReturn("mocked toString: " + name);
        return info;
    }
}
