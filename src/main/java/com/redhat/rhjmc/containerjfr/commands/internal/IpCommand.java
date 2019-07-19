package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

class IpCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final NetworkResolver resolver;

    @Inject IpCommand(ClientWriter cw, NetworkResolver resolver) {
        this.cw = cw;
        this.resolver = resolver;
    }

    @Override
    public String getName() {
        return "ip";
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
    public void execute(String[] args) throws Exception {
        cw.println(String.format("\t%s", resolver.getHostAddress()));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new StringOutput(resolver.getHostAddress());
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }
}