package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;

@Singleton
class ScanTargetsCommand implements SerializableCommand {

    private final PlatformClient platformClient;
    private final ClientWriter cw;

    @Inject
    ScanTargetsCommand(PlatformClient platformClient, ClientWriter cw) {
        this.platformClient = platformClient;
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "scan-targets";
    }

    @Override
    public boolean isAvailable() {
        return true;
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
    public void execute(String[] args) throws Exception {
        platformClient.listDiscoverableServices().forEach(s -> cw.println(String.format("%s -> %s:%d", s.getAlias(), s.getConnectUrl(), s.getPort())));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new ListOutput<>(platformClient.listDiscoverableServices());
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }
}
