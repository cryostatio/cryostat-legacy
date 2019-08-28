package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.WebServer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@ExtendWith(MockitoExtension.class)
class DeleteCommandTest {

    DeleteCommand command;
    @Mock
    ClientWriter cw;
    @Mock IRecordingDescriptor recordingDescriptor;
    @Mock
    JFRConnection connection;
    @Mock
    WebServer exporter;

    @BeforeEach
    void setup() throws FlightRecorderException {
        command = new DeleteCommand(cw, exporter);
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
        verify(exporter).removeRecording(recordingDescriptor);
    }

    @Test
    void shouldReturnSerializedSuccess() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");
        
        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{"foo-recording"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        verify(connection.getService()).close(recordingDescriptor);
        verify(exporter).removeRecording(recordingDescriptor);
    }

    @Test
    void shouldNotCloseUnnamedRecording() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        command.execute(new String[]{"bar-recording"});
        verify(connection.getService(), never()).close(recordingDescriptor);
        verifyZeroInteractions(exporter);
        verify(cw).println("No recording with name \"bar-recording\" found");
    }

    @Test
    void shouldReturnSerializedFailure() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{"bar-recording"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat((((SerializableCommand.FailureOutput) out).getPayload()), Matchers.equalTo("No recording with name \"bar-recording\" found"));

        verify(connection.getService(), never()).close(recordingDescriptor);
        verifyZeroInteractions(exporter);
    }

    @Test
    void shouldReturnSerializedException() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");
        doThrow(FlightRecorderException.class).when(service).close(Mockito.any());

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{"foo-recording"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat((((SerializableCommand.ExceptionOutput) out).getPayload()), Matchers.equalTo("FlightRecorderException: "));
    }

    @Test
    void shouldValidateCorrectArgc() {
        assertTrue(command.validate(new String[1]));
    }

    @ParameterizedTest
    @ValueSource(ints={
        0, 2
    })
    void shouldInvalidateIncorrectArgc(int c) {
        assertFalse(command.validate(new String[c]));
        verify(cw).println("Expected one argument: recording name");
    }

}
