package com.redhat.rhjmc.containerjfr.commands.internal;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.platform.Platform;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

@Singleton
class ScanTargetsCommand implements SerializableCommand {

    private final PlatformClient platformClient;
    private final ClientWriter cw;

    @Inject
    ScanTargetsCommand(Platform platform, ClientWriter cw) {
        this.platformClient = platform.getClient();
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
        platformClient.listDiscoverableServices().forEach(s -> cw.println(String.format("%s -> %s", s.getHostname(), s.getIp())));
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
