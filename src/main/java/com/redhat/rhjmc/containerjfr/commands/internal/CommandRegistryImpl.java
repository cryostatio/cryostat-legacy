package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.apache.commons.lang3.StringUtils;

class CommandRegistryImpl implements CommandRegistry {

    private final ClientWriter cw;
    private final Map<String, Command> commandMap = new TreeMap<>();

    CommandRegistryImpl(ClientWriter cw, Set<Command> commands) {
        this.cw = cw;
        for (Command command : commands) {
            String commandName = command.getName();
            if (commandMap.containsKey(commandName)) {
                throw new CommandDefinitionException(
                        commandName, command.getClass(), commandMap.get(commandName).getClass());
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
        return this.commandMap.values().stream()
                .filter(Command::isAvailable)
                .map(Command::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public void execute(String commandName, String[] args) throws Exception {
        if (!isCommandRegistered(commandName) || !isCommandAvailable(commandName)) {
            return;
        }
        commandMap.get(commandName).execute(args);
    }

    @Override
    public boolean validate(String commandName, String[] args) {
        return isCommandRegistered(commandName) && commandMap.get(commandName).validate(args);
    }

    private boolean isCommandRegistered(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        boolean registered = getRegisteredCommandNames().contains(commandName);
        if (!registered) {
            cw.println(String.format("Command \"%s\" not recognized", commandName));
        }
        return registered;
    }

    @Override
    public boolean isCommandAvailable(String commandName) {
        if (StringUtils.isBlank(commandName)) {
            return false;
        }
        boolean available = getAvailableCommandNames().contains(commandName);
        if (!available) {
            cw.println(String.format("Command \"%s\" not available", commandName));
        }
        return available;
    }

    @SuppressWarnings("serial")
    public static class CommandDefinitionException extends RuntimeException {
        public CommandDefinitionException(
                String commandName, Class<? extends Command> cmd1, Class<? extends Command> cmd2) {
            super(
                    String.format(
                            "\"%s\" command definitions provided by class %s AND class %s",
                            commandName, cmd1.getCanonicalName(), cmd2.getCanonicalName()));
        }
    }
}
