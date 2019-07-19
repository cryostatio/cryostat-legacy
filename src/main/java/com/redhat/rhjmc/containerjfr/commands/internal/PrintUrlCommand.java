package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;

@Singleton
class PrintUrlCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final RecordingExporter exporter;

    @Inject PrintUrlCommand(ClientWriter cw, RecordingExporter exporter) {
        this.cw = cw;
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "url";
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println(exporter.getHostUrl().toString());
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new StringOutput(exporter.getHostUrl().toString());
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
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

}