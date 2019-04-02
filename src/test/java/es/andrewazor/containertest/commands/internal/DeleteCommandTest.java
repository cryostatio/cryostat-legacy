package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.net.JMCConnection;

@ExtendWith(MockitoExtension.class)
class DeleteCommandTest extends TestBase {

    private DeleteCommand command;
    @Mock private IRecordingDescriptor recordingDescriptor;
    @Mock private JMCConnection connection;

    @BeforeEach
    void setup() throws FlightRecorderException {
        command = new DeleteCommand(mockClientWriter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDelete() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("delete"));
    }

    @Test
    void shouldCloseNamedRecording() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");
        
        command.execute(new String[]{"foo-recording"});
        verify(connection.getService()).close(recordingDescriptor);
    }

    @Test
    void shouldNotCloseUnnamedRecording() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");
        
        command.execute(new String[]{"bar-recording"});
        verify(connection.getService(), never()).close(recordingDescriptor);
        assertThat(stdout(), equalTo("No recording with name \"bar-recording\" found\n"));
    }

    @Test
    void shouldDisallowZeroArgs() {
        assertFalse(command.validate(new String[0]));
    }

    @Test
    void shouldAllowOneArg() {
        assertTrue(command.validate(new String[1]));
    }

    @Test
    void shouldDisallowTwoArgs() {
        assertFalse(command.validate(new String[2]));
    }

}
