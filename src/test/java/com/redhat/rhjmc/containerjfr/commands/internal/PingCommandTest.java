package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.mockito.Mockito.verify;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PingCommandTest {

    PingCommand command;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        command = new PingCommand(cw);
    }

    @Test
    void shouldBeNamedPing() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("ping"));
    }

    @Test
    void shouldExpectNoArgs() {
        MatcherAssert.assertThat(command.validate(new String[0]), Matchers.is(true));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2,
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
    void shouldReturnSuccessOutput() {
        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));
    }

    @Test
    void shouldEchoResponse() throws Exception {
        command.execute(new String[0]);
        verify(cw).println("\tpong");
    }
}
