package es.andrewazor.dockertest.commands.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;
import es.andrewazor.dockertest.commands.CommandRegistry;

public class CommandRegistryImpl implements CommandRegistry {

    // TODO: implement something smarter than this hardcoded list
    static final List<Class<? extends Command>> COMMANDS = Arrays.asList(
        HelpCommand.class,

        DownloadCommand.class,
        DumpCommand.class,
        ListCommand.class,
        ListOptionsCommand.class,
        WaitForCommand.class
    );

    private final Map<String, Class<? extends Command>> classMap = new HashMap<String, Class<? extends Command>>();
    private final IFlightRecorderService service;
    private final IConnectionHandle handle;

    public CommandRegistryImpl(IFlightRecorderService service, IConnectionHandle handle) throws Exception {
        for (Class<? extends Command> klazz : COMMANDS) {
            Command instance = createInstance(klazz);
            if (classMap.containsKey(instance.getName())) {
                throw new CommandDefinitionException(instance.getName(), klazz, classMap.get(instance.getName()));
            }
            classMap.put(instance.getName(), klazz);
        }
        this.service = service;
        this.handle = handle;
    }

    @Override
    public void execute(String commandName, String[] args) throws Exception {
        if (!classMap.containsKey(commandName)) {
            System.out.println(String.format("Command \"%s\" not recognized", commandName));
            return;
        }
        createInstance(classMap.get(commandName)).execute(args);
    }

    private Command createInstance(Class<? extends Command> klazz) throws Exception {
        return (Command) klazz.getDeclaredConstructor(IFlightRecorderService.class, IConnectionHandle.class).newInstance(service, handle);
    }

    public static class CommandDefinitionException extends Exception {
        public CommandDefinitionException(String commandName, Class<? extends Command> cmd1, Class<? extends Command> cmd2) {
            super(String.format("\"%s\" command definitions provided by class %s AND class %s",
                        commandName, cmd1.getCanonicalName(), cmd2.getCanonicalName()));
        }
    }
}
