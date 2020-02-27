package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.TemporalUnit;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class SaveRecordingCommandTest {

    @Mock ClientWriter cw;
    @Mock Clock clock;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    SaveRecordingCommand command;

    @BeforeEach
    void setup() {
        command = new SaveRecordingCommand(cw, clock, fs, recordingsPath);
    }

    @Test
    void shouldBeNamedSave() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("save"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldNotValidateWrongArgCounts(int count) {
        Assertions.assertFalse(command.validate(new String[count]));
        verify(cw).println("Expected one argument: recording name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "recording", "some-name", "another_name", "123", "abc123"})
    void shouldValidateRecordingNames(String recordingName) {
        Assertions.assertTrue(command.validate(new String[] {recordingName}));
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "some recording", ""})
    void shouldNotValidateInvalidRecordingNames(String recordingName) {
        Assertions.assertFalse(command.validate(new String[] {recordingName}));
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
        command.execute(new String[] {"foo"});

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
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        command.connectionChanged(connection);
        command.execute(new String[] {"foo"});

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(cw).println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndSaveDuplicatedRecording() throws Exception {
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");
        when(fs.exists(savePath)).thenReturn(false).thenReturn(true).thenReturn(false);

        command.connectionChanged(connection);
        command.execute(new String[] {"foo"});
        command.execute(new String[] {"foo"});

        verify(service, Mockito.times(2)).getAvailableRecordings();
        verify(fs, Mockito.times(2)).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.1.jfr");
        InOrder inOrder = Mockito.inOrder(cw);
        inOrder.verify(cw)
                .println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        inOrder.verify(cw)
                .println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.1.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndSaveRecordingWithExtension() throws Exception {
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo.jfr");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        command.connectionChanged(connection);
        command.execute(new String[] {"foo.jfr"});

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(cw).println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndReturnSerializedFailureIfRecordingNotFound()
            throws FlightRecorderException {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.FailureOutput) out).getPayload(),
                Matchers.equalTo("Recording with name \"foo\" not found"));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedException() throws FlightRecorderException {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenThrow(NullPointerException.class);

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndSaveRecordingAndReturnSerializedRecordingName() throws Exception {
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        command.connectionChanged(connection);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.StringOutput) out).getPayload(),
                Matchers.equalTo("some-host-svc-local_foo_20191129T112233Z.jfr"));

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verifyNoMoreInteractions(service);
        verifyZeroInteractions(cw);
    }
}
