package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IsConnectedCommandTest {

    IsConnectedCommand command;
    @Mock
    ClientWriter cw;
    @Mock
    JMCConnection conn;

    @BeforeEach
    void setup() {
        command = new IsConnectedCommand(cw);
    }

    @Test
    void shouldBeNamedPing() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("is-connected"));
    }

    @Test
    void shouldExpectNoArgs() {
        MatcherAssert.assertThat(command.validate(new String[0]), Matchers.is(true));
    }

    @ParameterizedTest
    @ValueSource(ints = {
        1,
        2,
    })
    void shouldNotExpectArgs(int argc) {
        MatcherAssert.assertThat(command.validate(new String[argc]), Matchers.is(false));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldBeAvailable() {
        MatcherAssert.assertThat(command.isAvailable(), Matchers.is(true));
    }

    @Test
    void shouldReturnStringOutput() {
        SerializableCommand.Output<?> outA = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outA, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outA.getPayload(), Matchers.equalTo("false"));

        when(conn.getHost()).thenReturn("someHost");
        when(conn.getPort()).thenReturn(1234);
        command.connectionChanged(conn);
        SerializableCommand.Output<?> outB = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outB, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outB.getPayload(), Matchers.equalTo("someHost:1234"));

        command.connectionChanged(null);
        SerializableCommand.Output<?> outC = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outC, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outC.getPayload(), Matchers.equalTo("false"));
    }

    @Test
    void shouldEchoStatus() throws Exception {
        command.execute(new String[0]);

        when(conn.getHost()).thenReturn("someHost");
        when(conn.getPort()).thenReturn(1234);
        command.connectionChanged(conn);
        command.execute(new String[0]);

        command.connectionChanged(null);
        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("\tDisconnected");
        inOrder.verify(cw).println("\tsomeHost:1234");
        inOrder.verify(cw).println("\tDisconnected");
    }

}