package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.JMCConnectionToolkit;

@ExtendWith(MockitoExtension.class)
class ConnectCommandTest {

    private ConnectCommand command;
    @Mock private ConnectionListener listener;
    @Mock private DisconnectCommand disconnect;
    @Mock private JMCConnectionToolkit connectionToolkit;

    @BeforeEach
    void setup() {
        command = new ConnectCommand(Collections.singleton(listener), disconnect, connectionToolkit);
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
    void emptyStringIsInvalid() {
        assertFalse(command.validate(new String[]{""}));
    }

    @Test
    void hostnameIsValid() {
        assertTrue(command.validate(new String[]{"some.host"}));
    }

    @Test
    void hostnameWithPortIsValid() {
        assertTrue(command.validate(new String[]{"some.host:8080"}));
    }

    @Test
    void hostnameWithDelimiterButNoPortIsInvalid() {
        assertFalse(command.validate(new String[]{"some.host:"}));
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

}