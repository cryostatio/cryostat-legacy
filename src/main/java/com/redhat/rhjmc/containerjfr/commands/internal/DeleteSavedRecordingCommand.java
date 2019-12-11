package com.redhat.rhjmc.containerjfr.commands.internal;

import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class DeleteSavedRecordingCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;

    @Inject
    DeleteSavedRecordingCommand(
            ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "delete-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        if (fs.deleteIfExists(recordingsPath.resolve(name))) {
            cw.println(String.format("\"%s\" deleted", name));
        } else {
            cw.println(String.format("Could not delete saved recording \"%s\"", name));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];
            if (fs.deleteIfExists(recordingsPath.resolve(name))) {
                return new SuccessOutput();
            } else {
                return new FailureOutput(
                        String.format("Could not delete saved recording \"%s\"", name));
            }
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument: recording name");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return fs.isDirectory(recordingsPath);
    }
}
