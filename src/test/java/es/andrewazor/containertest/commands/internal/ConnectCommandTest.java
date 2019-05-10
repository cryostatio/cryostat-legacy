package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommand.StringOutput;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.net.JMCConnectionToolkit;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class ConnectCommandTest {

    ConnectCommand command;
    @Mock ClientWriter cw;
    @Mock ConnectionListener listener;
    @Mock DisconnectCommand disconnect;
    @Mock JMCConnectionToolkit connectionToolkit;

    @BeforeEach
    void setup() {
        command = new ConnectCommand(cw, Collections.singleton(listener), disconnect, connectionToolkit);
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldBeNamedConnect() {
        assertThat(command.getName(), Matchers.equalTo("connect"));
    }

    @Test
    void shouldExpectOneArg() {
        assertFalse(command.validate(new String[0]));
        assertTrue(command.validate(new String[]{ "foo" }));
        assertFalse(command.validate(new String[2]));
        verify(cw, Mockito.times(2)).println("Expected one argument: host name/URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "some.host:",
        ":",
        "some.host:abc"
    })
    void shouldNotValidateInvalidIdentifiers(String id) {
        assertFalse(command.validate(new String[] { id }), id);
        verify(cw).println(id + " is an invalid host name/URL");
    }

    @Test
    void shouldNotValidateNull() {
        assertFalse(command.validate(new String[] { null }));
        verify(cw).println("Expected one argument: host name/URL");
    }

    @Test
    void shouldNotValidateEmptyString() {
        assertFalse(command.validate(new String[] { " " }));
        verify(cw).println("Expected one argument: host name/URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "some.host",
        "some.host:8080",
    })
    void shouldValidateValidIdentifiers(String id) {
        assertTrue(command.validate(new String[] { id }));
    }

    @Test
    void shouldConnectViaConnectionToolkit() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JMCConnection mockConnection = mock(JMCConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockConnection);

        command.execute(new String[] { "foo:5" });

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JMCConnection> connectionCaptor = ArgumentCaptor.forClass(JMCConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(5));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldUseDefaultPortIfUnspecified() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JMCConnection mockConnection = mock(JMCConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockConnection);

        command.execute(new String[] { "foo" });

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JMCConnection> connectionCaptor = ArgumentCaptor.forClass(JMCConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JMCConnection.DEFAULT_PORT));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldReturnStringOutput() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JMCConnection mockConnection = mock(JMCConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockConnection);

        Output out = command.serializableExecute(new String[] { "foo" });
        assertThat(out, instanceOf(StringOutput.class));
        assertThat(((StringOutput) out).getMessage(), equalTo("foo"));

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JMCConnection> connectionCaptor = ArgumentCaptor.forClass(JMCConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JMCConnection.DEFAULT_PORT));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JMCConnection mockConnection = mock(JMCConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt())).thenThrow(IOException.class);

        Output out = command.serializableExecute(new String[] { "foo" });
        assertThat(out, instanceOf(ExceptionOutput.class));
        assertThat(((ExceptionOutput) out).getExceptionMessage(), equalTo("IOException: "));

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);

        verifyNoMoreInteractions(listener);
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JMCConnection.DEFAULT_PORT));
    }

}