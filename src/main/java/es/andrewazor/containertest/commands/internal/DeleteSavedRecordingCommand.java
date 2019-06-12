package es.andrewazor.containertest.commands.internal;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class DeleteSavedRecordingCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Path recordingsPath;

    @Inject
    DeleteSavedRecordingCommand(ClientWriter cw, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "delete-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        if (Files.deleteIfExists(recordingsPath.resolve(name))) {
            cw.println(String.format("%s deleted", name));
        } else {
            cw.println(String.format("Could not delete saved recording %s", name));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];
            if (Files.deleteIfExists(recordingsPath.resolve(name))) {
                cw.println(String.format("%s deleted", name));
                return new SuccessOutput();
            } else {
                return new FailureOutput(String.format("Could not delete saved recording %s", name));
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
        return Files.isDirectory(recordingsPath);
    }

}
