package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.UnknownHostException;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintUrlCommandTest {

    PrintUrlCommand command;
    @Mock
    ClientWriter cw;
    @Mock
    RecordingExporter exporter;

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
        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("mock-url"));
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        when(exporter.getHostUrl()).thenThrow(UnknownHostException.class);
        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("UnknownHostException: "));
    }

}