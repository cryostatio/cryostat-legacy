package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.SerializableCommand.ListOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommandRegistry;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class HelpCommandTest {

    HelpCommand command;
    @Mock ClientWriter cw;
    @Mock CommandRegistry registry;
    @Mock SerializableCommandRegistry serializableRegistry;

    @BeforeEach
    void setup() {
        command = new HelpCommand(cw, () -> registry, () -> serializableRegistry);
    }

    @Test
    void shouldBeNamedHelp() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("help"));
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
    void shouldPrintAvailableCommandNames() throws Exception {
        Set<String> names = new HashSet<>(Arrays.asList(
            "foo",
            "bar"
        ));

        when(registry.getAvailableCommandNames()).thenReturn(names);
        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available commands:");
        inOrder.verify(cw).println("\tbar");
        inOrder.verify(cw).println("\tfoo");
    }

    @Test
    void shouldReturnListOutput() throws Exception {
        List<String> names = Arrays.asList(
            "bar",
            "foo"
        );

        when(serializableRegistry.getAvailableCommandNames()).thenReturn(new HashSet<>(names));
        Output out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(ListOutput.class));
        MatcherAssert.assertThat(((ListOutput<String>) out).getData(), Matchers.equalTo(names));
    }

}