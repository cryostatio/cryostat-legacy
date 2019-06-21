package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SavedRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
import com.redhat.rhjmc.containerjfr.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

@Singleton
class ListSavedRecordingsCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;
    private final RecordingExporter exporter;

    @Inject
    ListSavedRecordingsCommand(ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath,
            RecordingExporter exporter) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "list-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Saved recordings:");
        List<String> saved = fs.listDirectoryChildren(recordingsPath);
        if (saved.isEmpty()) {
            cw.println("\tNone");
        }
        for (String file : saved) {
            cw.println(String.format("\t%s", file));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        List<SavedRecordingDescriptor> recordings = new ArrayList<>();
        try {
            for (String name : fs.listDirectoryChildren(recordingsPath)) {
                recordings.add(new SavedRecordingDescriptor(name, exporter.getDownloadURL(name)));
            }
        } catch (IOException e) {
            return new ExceptionOutput(e);
        }
        return new ListOutput<>(recordings);
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
        return fs.isDirectory(recordingsPath);
    }

}
