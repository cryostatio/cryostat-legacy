package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.commands.CommandRegistry;

@ExtendWith(MockitoExtension.class)
class HelpCommandTest extends TestBase {

    private HelpCommand command;
    @Mock private CommandRegistry registry;

    @BeforeEach
    void setup() {
        command = new HelpCommand(mockClientWriter, () -> registry);
    }

    @Test
    void shouldBeNamedHelp() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("help"));
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
    void shouldPrintAvailableCommandNames() throws Exception {
        Set<String> names = new HashSet<>(Arrays.asList(
            "foo",
            "bar"
        ));

        when(registry.getAvailableCommandNames()).thenReturn(names);
        command.execute(new String[0]);

        MatcherAssert.assertThat(stdout(), Matchers.allOf(
            Matchers.containsString("Available commands:\n"),
            Matchers.containsString("\tbar\n"),
            Matchers.containsString("\tfoo\n")
        ));
    }

}