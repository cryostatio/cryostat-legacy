package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
class SaveRecordingCommandTest {

    @Mock
    ClientWriter cw;
    @Mock
    FileSystem fs;
    @Mock Path recordingsPath;
    @Mock
    JMCConnection connection;
    @Mock IFlightRecorderService service;
    SaveRecordingCommand command;

    @BeforeEach
    void setup() {
        command = new SaveRecordingCommand(cw, fs, recordingsPath);
    }

    @Test
    void shouldBeNamedSave() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("save"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 2 })
    void shouldNotValidateWrongArgCounts(int count) {
        Assertions.assertFalse(command.validate(new String[count]));
        verify(cw).println("Expected one argument: recording name");
    }

    @ParameterizedTest
    @ValueSource(strings = { "foo", "recording", "some-name", "another_name", "123", "abc123" })
    void shouldValidateRecordingNames(String recordingName) {
        Assertions.assertTrue(command.validate(new String[] { recordingName }));
    }

    @ParameterizedTest
    @ValueSource(strings = { ".", "some recording", "" })
    void shouldNotValidateInvalidRecordingNames(String recordingName) {
        Assertions.assertFalse(command.validate(new String[] { recordingName }));
        verify(cw).println(recordingName + " is an invalid recording name");
    }

    @Test
    void shouldNotBeAvailableWhenDisconnected() {
        Assertions.assertFalse(command.isAvailable());
    }

    @Test
    void shouldNotBeAvailableWhenConnectedButRecordingsPathNotDirectory() {
        command.connectionChanged(connection);
        when(fs.isDirectory(Mockito.any())).thenReturn(false);
        Assertions.assertFalse(command.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenConnectedAndRecordingsPathIsDirectory() {
        command.connectionChanged(connection);
        when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldExecuteAndPrintMessageIfRecordingNotFound() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.connectionChanged(connection);
        command.execute(new String[] { "foo" });

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        verify(cw).println("Recording with name \"foo\" not found");
        verifyNoMoreInteractions(cw);
    }

    @Test
    void shouldExecuteAndSaveRecording() throws Exception {
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);

        command.connectionChanged(connection);
        command.execute(new String[] { "foo" });

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath, StandardCopyOption.REPLACE_EXISTING);
        verify(recordingsPath).resolve("foo.jfr");
        verifyNoMoreInteractions(service);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedFailureIfRecordingNotFound() throws FlightRecorderException {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] { "foo" });

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.FailureOutput) out).getPayload(), Matchers.equalTo("Recording with name \"foo\" not found"));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedException() throws FlightRecorderException {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenThrow(NullPointerException.class);

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] { "foo" });

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndSaveRecordingAndReturnSerializedSuccess() throws Exception {
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] { "foo" });

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath, StandardCopyOption.REPLACE_EXISTING);
        verify(recordingsPath).resolve("foo.jfr");
        verifyNoMoreInteractions(service);
        verifyZeroInteractions(cw);
    }

}
