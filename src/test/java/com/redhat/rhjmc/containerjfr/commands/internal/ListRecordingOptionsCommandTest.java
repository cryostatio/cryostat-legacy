package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableOptionDescriptor;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

@ExtendWith(MockitoExtension.class)
class ListRecordingOptionsCommandTest {

    ListRecordingOptionsCommand command;
    @Mock
    ClientWriter cw;
    @Mock
    JFRConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new ListRecordingOptionsCommand(cw);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedListRecordingOptions() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-recording-options"));
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

    @Test
    void shouldPrintRecordingOptions() throws Exception {
        IOptionDescriptor<String> descriptor = mock(IOptionDescriptor.class);
        when(descriptor.toString()).thenReturn("foo-option-toString");
        Map<String, IOptionDescriptor<?>> options = Map.of("foo-option", descriptor);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenReturn(options);

        command.execute(new String[0]);
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recording options:");
        inOrder.verify(cw).println("\tfoo-option : foo-option-toString");
    }

    @Test
    void shouldReturnMapOutput() throws Exception {
        IOptionDescriptor<String> descriptor = mock(IOptionDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.getDescription()).thenReturn("Foo Option");
        when(descriptor.getDefault()).thenReturn("bar");
        Map<String, IOptionDescriptor<?>> options = Map.of("foo-option", descriptor);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenReturn(options);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.MapOutput.class));
        MatcherAssert.assertThat(out.getPayload(),
                Matchers.equalTo(Map.of("foo-option", new SerializableOptionDescriptor(descriptor))));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenThrow(FlightRecorderException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("FlightRecorderException: "));
    }

}
