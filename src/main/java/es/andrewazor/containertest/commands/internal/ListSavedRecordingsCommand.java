package es.andrewazor.containertest.commands.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListSavedRecordingsCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Path recordingsPath;

    @Inject
    ListSavedRecordingsCommand(ClientWriter cw, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "list-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Saved recordings:");
        String[] saved = recordingsPath.toFile().list();
        if (saved.length == 0) {
            cw.println("\tNone");
        }
        for (String file : saved) {
            cw.println(String.format("\t%s", file));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        return new ListOutput<String>(Arrays.asList(recordingsPath.toFile().list()));
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
        return Files.isDirectory(recordingsPath);
    }

}
