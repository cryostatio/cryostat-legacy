package es.andrewazor.containertest.commands;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.internal.CommandRegistryImpl;

public class CommandRegistryFactory {
    private CommandRegistryFactory() { }

    public static CommandRegistry createNewInstance(JMCConnection connection) throws Exception {
        return new CommandRegistryImpl(connection);
    }
}
