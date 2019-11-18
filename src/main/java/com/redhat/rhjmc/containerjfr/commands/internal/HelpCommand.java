package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.Lazy;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

class HelpCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Lazy<CommandRegistry> registry;
    private final Lazy<SerializableCommandRegistry> serializableRegistry;

    @Inject
    HelpCommand(ClientWriter cw, Lazy<CommandRegistry> commandRegistry,
            Lazy<SerializableCommandRegistry> serializableCommandRegistry) {
        this.cw = cw;
        this.registry = commandRegistry;
        this.serializableRegistry = serializableCommandRegistry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Available commands:");
        registry.get().getAvailableCommandNames().forEach(this::printCommand);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        return new ListOutput<>(new ArrayList<>(serializableRegistry.get().getAvailableCommandNames()));
    }

    private void printCommand(String cmd) {
        cw.println(String.format("\t%s", cmd));
    }
}

