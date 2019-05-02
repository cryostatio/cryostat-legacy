package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class ListRecordingOptionsCommandTest {

    ListRecordingOptionsCommand command;
    @Mock ClientWriter cw;
    @Mock JMCConnection connection;
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

    @SuppressWarnings("unchecked")
    @Test
    void shouldPrintRecordingOptions() throws Exception {
        Map options = new HashMap();
        IOptionDescriptor descriptor = mock(IOptionDescriptor.class);
        when(descriptor.toString()).thenReturn("foo-option-toString");
        options.put("foo-option", descriptor);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenReturn(options);

        command.execute(new String[0]);
        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available recording options:");
        inOrder.verify(cw).println("\tfoo-option : foo-option-toString");
    }

}