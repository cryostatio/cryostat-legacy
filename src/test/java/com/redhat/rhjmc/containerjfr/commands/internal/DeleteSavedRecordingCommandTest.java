package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
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

@ExtendWith(MockitoExtension.class)
class DeleteSavedRecordingCommandTest {

    @Mock
    ClientWriter cw;
    @Mock
    FileSystem fs;
    @Mock
    Path recordingsPath;
    DeleteSavedRecordingCommand command;

    @BeforeEach
    void setup() {
        command = new DeleteSavedRecordingCommand(cw, fs, recordingsPath);
    }

    @Test
    void shouldBeNamedDeleteSaved() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("delete-saved"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 2 })
    void shouldNotValidateWrongArgCounts(int count) {
        Assertions.assertFalse(command.validate(new String[count]));
        verify(cw).println("Expected one argument: recording name");
    }

    @Test
    void shouldValidateRecordingNameArg() {
        Assertions.assertTrue(command.validate(new String[] { "foo" }));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldBeAvailableIfRecordingsPathIsDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(true);

        Assertions.assertTrue(command.isAvailable());

        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldNotBeAvailableIfRecordingsPathIsNotDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(false);

        Assertions.assertFalse(command.isAvailable());

        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldExecuteAndPrintMessageOnSuccess() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(true);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        command.execute(new String[]{ "foo" });

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
        verify(cw).println("\"foo\" deleted");
    }

    @Test
    void shouldExecuteAndPrintMessageOnFailure() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(false);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        command.execute(new String[]{ "foo" });

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
        verify(cw).println("Could not delete saved recording \"foo\"");
    }

    @Test
    void shouldExecuteAndReturnSerializedSuccess() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(true);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{ "foo" });

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
    }

    @Test
    void shouldExecuteAndReturnSerializedFailure() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(false);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{ "foo" });

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.FailureOutput) out).getPayload(), Matchers.equalTo("Could not delete saved recording \"foo\""));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
    }

    @Test
    void shouldExecuteAndReturnSerializedException() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenThrow(IOException.class);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{ "foo" });

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
    }

}