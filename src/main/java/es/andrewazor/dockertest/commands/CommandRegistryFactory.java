package es.andrewazor.dockertest.commands;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.internal.CommandRegistryImpl;

public class CommandRegistryFactory {
    private CommandRegistryFactory() { }

    public static CommandRegistry createNewInstance(IFlightRecorderService service) throws Exception {
        return new CommandRegistryImpl(service);
    }
}
