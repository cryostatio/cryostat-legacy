package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.UnknownHostException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommand.StringOutput;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class PrintUrlCommandTest {

    PrintUrlCommand command;
    @Mock ClientWriter cw;
    @Mock RecordingExporter exporter;

    @BeforeEach
    void setup() {
        command = new PrintUrlCommand(cw, exporter);
    }

    @Test
    void shouldBeNamedUrl() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("url"));
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
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
    void shouldPrintRecordingExporterHostURL() throws Exception {
        verifyZeroInteractions(exporter);
        URL url = mock(URL.class);
        when(url.toString()).thenReturn("mock-url");
        when(exporter.getHostUrl()).thenReturn(url);
        command.execute(new String[0]);
        verify(cw).println("mock-url");
        verifyNoMoreInteractions(cw);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldReturnStringOutput() throws Exception {
        verifyZeroInteractions(exporter);
        URL url = mock(URL.class);
        when(url.toString()).thenReturn("mock-url");
        when(exporter.getHostUrl()).thenReturn(url);
        Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(StringOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("mock-url"));
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(exporter.getHostUrl()).thenThrow(UnknownHostException.class);
        Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("UnknownHostException: "));
    }

}