package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class DisconnectCommandTest {

    DisconnectCommand command;
    @Mock ConnectionListener listener;
    @Mock JMCConnection connection;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        command = new DisconnectCommand(() -> Collections.singleton(listener), cw);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDisconnect() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("disconnect"));
    }

    @Test
    void shouldExpectZeroArgs() {
        assertTrue(command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldNotifyListeners() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.execute(new String[0]);
        verify(listener).connectionChanged(null);
        verify(connection).disconnect();
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldHandleNoPreviousConnection() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.connectionChanged(null);
        command.execute(new String[0]);
        verify(listener).connectionChanged(null);
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldHandleDoubleDisconnect() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.execute(new String[0]);
        command.connectionChanged(null);
        command.execute(new String[0]);
        verify(listener, Mockito.times(2)).connectionChanged(null);
        verify(connection, Mockito.times(1)).disconnect();
        verify(cw).println("No active connection");
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

}