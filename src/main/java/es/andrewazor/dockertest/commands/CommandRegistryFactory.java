package es.andrewazor.dockertest.commands;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.internal.CommandRegistryImpl;

public class CommandRegistryFactory {
    private CommandRegistryFactory() { }

    public static CommandRegistry createNewInstance(IFlightRecorderService service, IConnectionHandle handle) throws Exception {
        return new CommandRegistryImpl(service, handle);
    }
}
