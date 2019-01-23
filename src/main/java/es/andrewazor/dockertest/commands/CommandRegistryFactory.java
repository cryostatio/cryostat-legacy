package es.andrewazor.dockertest.commands;

import es.andrewazor.dockertest.JMCConnection;
import es.andrewazor.dockertest.commands.internal.CommandRegistryImpl;

public class CommandRegistryFactory {
    private CommandRegistryFactory() { }

    public static CommandRegistry createNewInstance(JMCConnection connection) throws Exception {
        return new CommandRegistryImpl(connection);
    }
}
