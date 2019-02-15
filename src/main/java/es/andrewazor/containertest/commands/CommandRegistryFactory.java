package es.andrewazor.containertest.commands;

import es.andrewazor.containertest.commands.internal.CommandRegistryImpl;

public class CommandRegistryFactory {

    private static CommandRegistry registry;

    private CommandRegistryFactory() { }

    public static CommandRegistry getInstance() throws Exception {
        if (registry == null) {
            registry = new CommandRegistryImpl();
        }
        return registry;
    }
}
