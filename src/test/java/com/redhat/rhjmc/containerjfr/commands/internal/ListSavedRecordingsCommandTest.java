package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SavedRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class ListSavedRecordingsCommandTest {

    @Mock
    ClientWriter cw;
    @Mock
    FileSystem fs;
    @Mock
    Path recordingsPath;
    @Mock
    RecordingExporter exporter;
    ListSavedRecordingsCommand command;

    @BeforeEach
    void setup() {
        command = new ListSavedRecordingsCommand(cw, fs, recordingsPath, exporter);
    }

    @Test
    void shouldBeNamedListSaved() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-saved"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    void shouldNotValidateWrongArgCounts(int count) {
        Assertions.assertFalse(command.validate(new String[count]));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldValidateEmptyArgs() {
        Assertions.assertTrue(command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldBeAvailableIfRecordingsPathIsDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(command.isAvailable());
        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldBeUnavailableIfRecordingsPathIsNotDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(false);
        Assertions.assertFalse(command.isAvailable());
        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldExecuteAndPrintMessageIfNoSavedRecordingsFound() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Collections.emptyList());

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw).println("\tNone");
    }

    @Test
    void shouldExecuteAndPrintSavedRecordings() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw).println("\tfoo");
        inOrder.verify(cw).println("\tbar");
    }

    @Test
    void shouldExecuteAndReturnSerializedMessageIfNoSavedRecordingsFound() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Collections.emptyList());

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.ListOutput) out).getPayload(), Matchers.equalTo(Collections.emptyList()));

        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedExceptionMessageIfThrows() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenThrow(IOException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));

        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedRecordingInfo() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));
        when(exporter.getDownloadURL(Mockito.anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0] + ".jfr";
            }
        });
        when(exporter.getReportURL(Mockito.anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return "/reports/" + invocation.getArguments()[0] + ".jfr";
            }
        });

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.ListOutput) out).getPayload(), Matchers.equalTo(
            Arrays.asList(
                new SavedRecordingDescriptor("foo", "foo.jfr", "/reports/foo.jfr"),
                new SavedRecordingDescriptor("bar", "bar.jfr", "/reports/bar.jfr")
            )
        ));

        verifyZeroInteractions(cw);
    }

}
