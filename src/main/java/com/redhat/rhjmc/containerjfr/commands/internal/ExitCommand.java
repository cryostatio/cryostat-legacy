package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

public class ExitCommand implements Command {

    public static final String NAME = "exit";

    private final ClientWriter cw;

    @Inject ExitCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return NAME;
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

    @Override
    public void execute(String[] args) { };

}