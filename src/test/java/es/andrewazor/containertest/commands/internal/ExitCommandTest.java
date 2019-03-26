package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExitCommandTest {

    private ExitCommand command;

    @BeforeEach
    void setup() {
        command = new ExitCommand();
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
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
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