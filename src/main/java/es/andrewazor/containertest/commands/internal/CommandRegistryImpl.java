package es.andrewazor.containertest.commands.internal;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.CommandRegistry;

public class CommandRegistryImpl implements CommandRegistry {

    // TODO: implement something smarter than this hardcoded list (annotation based?)
    private static final List<Class<? extends Command>> COMMANDS = Collections.unmodifiableList(Arrays.asList(
        HelpCommand.class,

        ConnectCommand.class,
        DisconnectCommand.class,
        DumpCommand.class,
        ExitCommand.class,
        HostnameCommand.class,
        IpCommand.class,
        ListCommand.class,
        ListEventTypesCommand.class,
        ListRecordingOptionsCommand.class,
        SearchEventsCommand.class,
        WaitForCommand.class,
        WaitForDownloadCommand.class
    ));

    private final Map<String, Class<? extends Command>> classMap = new TreeMap<String, Class<? extends Command>>();
    private JMCConnection connection;

    public CommandRegistryImpl() throws Exception {
        for (Class<? extends Command> klazz : COMMANDS) {
            Command instance = createInstance(klazz);
            String commandName = instance.getName();
            if (classMap.containsKey(commandName)) {
                throw new CommandDefinitionException(commandName, klazz, classMap.get(commandName));
            }
            classMap.put(commandName, klazz);
        }
    }

    @Override
    public Set<String> getRegisteredCommandNames() {
        return this.classMap.keySet();
    }

    @Override
    public void setConnection(JMCConnection connection) throws Exception {
        this.connection = connection;
        if (connection != null) {
            if (!connection.getService().isEnabled()) {
                connection.getService().enable();
            }
            connection.getRecordingExporter().restart();
        }
    }

    @Override
    public void execute(String commandName, String[] args) throws Exception {
        if (!classMap.containsKey(commandName)) {
            System.out.println(String.format("Command \"%s\" not recognized", commandName));
            return;
        }
        createInstance(classMap.get(commandName)).execute(args);
    }

    @Override
    public boolean validate(String commandName, String[] args) throws Exception {
        if (!classMap.containsKey(commandName)) {
            System.out.println(String.format("Command \"%s\" not recognized", commandName));
            return false;
        }
        return createInstance(classMap.get(commandName)).validate(args);
    }

    private Command createInstance(Class<? extends Command> klazz) throws Exception {
        for (Constructor<?> cons : klazz.getDeclaredConstructors()) {
            if (Arrays.deepEquals(new Class<?>[] { JMCConnection.class }, cons.getParameterTypes())) {
                return (Command) klazz.getDeclaredConstructor(JMCConnection.class).newInstance(connection);
            }
        }
        return (Command) klazz.getDeclaredConstructor().newInstance();
    }

    public static class CommandDefinitionException extends Exception {
        public CommandDefinitionException(String commandName, Class<? extends Command> cmd1, Class<? extends Command> cmd2) {
            super(String.format("\"%s\" command definitions provided by class %s AND class %s",
                        commandName, cmd1.getCanonicalName(), cmd2.getCanonicalName()));
        }
    }
}
