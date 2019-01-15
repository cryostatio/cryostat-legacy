package es.andrewazor.dockertest.commands.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;
import es.andrewazor.dockertest.commands.CommandRegistry;

public class CommandRegistryImpl implements CommandRegistry {

    private static final List<Class<? extends Command>> COMMANDS = Arrays.asList(
        DownloadCommand.class,
        DumpCommand.class,
        ListCommand.class,
        ListOptionsCommand.class
    );

    private final Map<String, Class<? extends Command>> classMap = new HashMap<String, Class<? extends Command>>();
    private final IFlightRecorderService service;

    public CommandRegistryImpl(IFlightRecorderService service) throws Exception {
        for (Class<? extends Command> klazz : COMMANDS) {
            Command instance = (Command) klazz.getDeclaredConstructor().newInstance();
            if (classMap.containsKey(instance.getName())) {
                throw new CommandDefinitionException(instance.getName(), klazz, classMap.get(instance.getName()));
            }
            classMap.put(instance.getName(), klazz);
        }
        this.service = service;
    }

    @Override
    public void execute(String commandName, String[] args) throws Exception {
        if (!classMap.containsKey(commandName)) {
            System.out.println(String.format("Command \"%s\" not recognized", commandName));
        }
        classMap.get(commandName).getDeclaredConstructor().newInstance().execute(service, args);
    }

    public static class CommandDefinitionException extends Exception {
        public CommandDefinitionException(String commandName, Class<? extends Command> cmd1, Class<? extends Command> cmd2) {
            super(String.format("Command \"%s\" definitions provided by class %s AND class %s", cmd1.getCanonicalName(), cmd2.getCanonicalName()));
        }
    }
}
