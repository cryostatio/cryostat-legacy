package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class ExitCommandTest {

    ExitCommand command;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        command = new ExitCommand(cw);
    }

    @Test
    void shouldBeNamedExit() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("exit"));
    }

    @Test
    void shouldExposeNameAsConstant() {
        MatcherAssert.assertThat(ExitCommand.NAME, Matchers.equalTo("exit"));
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
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldNotThrowOnExecute() {
        command.execute(new String[0]);
    }
}
