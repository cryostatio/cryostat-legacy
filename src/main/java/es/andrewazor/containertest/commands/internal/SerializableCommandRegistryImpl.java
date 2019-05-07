package es.andrewazor.containertest.commands.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommandRegistry;
import es.andrewazor.containertest.commands.internal.CommandRegistryImpl.CommandDefinitionException;

class SerializableCommandRegistryImpl implements SerializableCommandRegistry {

    private final Map<String, SerializableCommand> commandMap = new TreeMap<>();

    SerializableCommandRegistryImpl(Set<Command> allCommands) {
        Set<SerializableCommand> commands = new HashSet<>();
        allCommands.forEach(c -> {
            if (c instanceof SerializableCommand) {
                commands.add((SerializableCommand) c);
            }
        });
        for (SerializableCommand command : commands) {
            String commandName = command.getName();
            if (commandMap.containsKey(commandName)) {
                throw new CommandDefinitionException(commandName, command.getClass(),
                        commandMap.get(commandName).getClass());
            }
            commandMap.put(commandName, command);
        }
    }

    @Override
    public Set<String> getRegisteredCommandNames() {
        return this.commandMap.keySet();
    }

    @Override
    public Set<String> getAvailableCommandNames() {
        return this.commandMap.values().stream().filter(Command::isAvailable).map(Command::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public Output execute(String commandName, String[] args) {
        if (!isCommandRegistered(commandName) || !isCommandAvailable(commandName)) {
            return () -> false;
        }
        return commandMap.get(commandName).serializableExecute(args);
    }

    @Override
    public boolean validate(String commandName, String[] args) {
        return isCommandRegistered(commandName) && commandMap.get(commandName).validate(args);
    }

    private boolean isCommandRegistered(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        return getRegisteredCommandNames().contains(commandName);
    }

    @Override
    public boolean isCommandAvailable(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        return getAvailableCommandNames().contains(commandName);
    }
}
