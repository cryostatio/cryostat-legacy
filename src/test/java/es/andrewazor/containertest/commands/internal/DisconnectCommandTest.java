package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

@ExtendWith(MockitoExtension.class)
class DisconnectCommandTest {

    private DisconnectCommand command;
    @Mock private ConnectionListener listener;
    @Mock private JMCConnection connection;

    @BeforeEach
    void setup() {
        command = new DisconnectCommand(() -> Collections.singleton(listener));
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDisconnect() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("disconnect"));
    }

    @Test
    void shouldExpectZeroArgs() {
        assertTrue(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
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
        command.execute(new String[0]);
        verify(listener, Mockito.times(2)).connectionChanged(null);
        verify(connection, Mockito.times(2)).disconnect();
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

}