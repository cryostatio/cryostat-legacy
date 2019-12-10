package com.redhat.rhjmc.containerjfr.commands;

public interface SerializableCommandRegistry extends BaseCommandRegistry {
    SerializableCommand.Output execute(String commandName, String[] args);
}
