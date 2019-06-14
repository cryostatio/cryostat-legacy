package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableEventTypeInfo;
import com.redhat.rhjmc.containerjfr.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

@ExtendWith(MockitoExtension.class)
class ListEventTypesCommandTest {

    ListEventTypesCommand command;
    @Mock
    ClientWriter cw;
    @Mock
    JMCConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new ListEventTypesCommand(cw);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedListEventTypes() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-event-types"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
        verify(cw).println("No arguments expected");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPrintEventTypes() throws Exception {
        Collection eventTypes = Arrays.asList(
            createEvent("foo"),
            createEvent("bar")
        );

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn(eventTypes);

        command.execute(new String[0]);
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
        when(eventInfo.getHierarchicalCategory()).thenReturn(new String[] { "com", "example" });
        when(eventInfo.getOptionDescriptors()).thenReturn(Collections.emptyMap());

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn((Collection) Collections.singleton(eventInfo));

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(out.getPayload(),
                Matchers.equalTo(Collections.singletonList(new SerializableEventTypeInfo(eventInfo))));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenThrow(FlightRecorderException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }

    private static IEventTypeInfo createEvent(String name) {
        IEventTypeInfo info = mock(IEventTypeInfo.class);
        when(info.toString()).thenReturn("mocked toString: " + name);
        return info;
    }

}