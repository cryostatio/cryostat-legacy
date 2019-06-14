package com.redhat.rhjmc.containerjfr.commands;

public interface CommandRegistry extends BaseCommandRegistry {
    void execute(String commandName, String[] args) throws Exception;
}