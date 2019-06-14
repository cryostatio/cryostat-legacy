package es.andrewazor.containertest.commands.internal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.ListOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.jmc.serialization.SavedRecordingDescriptor;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.sys.FileSystem;
import es.andrewazor.containertest.tui.ClientWriter;

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

        Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(ListOutput.class));
        MatcherAssert.assertThat(((ListOutput) out).getPayload(), Matchers.equalTo(Collections.emptyList()));

        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedExceptionMessageIfThrows() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenThrow(IOException.class);

        Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));

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

        Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(ListOutput.class));
        MatcherAssert.assertThat(((ListOutput) out).getPayload(), Matchers.equalTo(
            Arrays.asList(
                new SavedRecordingDescriptor("foo", "foo.jfr"),
                new SavedRecordingDescriptor("bar", "bar.jfr")
            )
        ));

        verifyZeroInteractions(cw);
    }

}
