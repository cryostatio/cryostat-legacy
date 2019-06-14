package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

class PingCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    PingCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "ping";
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
        cw.println("\tpong");
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        return new SuccessOutput();
    }
}


