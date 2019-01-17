package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;

class HelpCommand extends AbstractCommand {
    HelpCommand(IFlightRecorderService service, IConnectionHandle handle) {
        super(service, handle);
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available commands:");
        for (Class<? extends Command> klazz : CommandRegistryImpl.COMMANDS) {
            Command instance = (Command) klazz.getDeclaredConstructor(IFlightRecorderService.class).newInstance(service);
            System.out.println(String.format("\t%s", instance.getName()));
        }
    }
}

