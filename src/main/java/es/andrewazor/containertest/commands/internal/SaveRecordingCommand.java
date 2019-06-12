package es.andrewazor.containertest.commands.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class SaveRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Path recordingsPath;

    @Inject
    SaveRecordingCommand(ClientWriter cw, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "save";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];

        Optional<IRecordingDescriptor> descriptor = getDescriptorByName(name);
        if (descriptor.isPresent()) {
            Files.copy(
                getService().openStream(descriptor.get(), false),
                recordingsPath.resolve(String.format("%s.jfr", descriptor.get().getName())),
                StandardCopyOption.REPLACE_EXISTING
            );
        } else {
            cw.println(String.format("Recording with name \"%s\" not found", name));
            return;
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        String name = args[0];

        try {
            Optional<IRecordingDescriptor> descriptor = getDescriptorByName(name);
            if (descriptor.isPresent()) {
                Files.copy(
                    getService().openStream(descriptor.get(), false),
                    recordingsPath.resolve(String.format("%s.jfr", descriptor.get().getName())),
                    StandardCopyOption.REPLACE_EXISTING
                );
                return new SuccessOutput();
            } else {
                return new FailureOutput(String.format("Recording with name \"%s\" not found", name));
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

        String name = args[0];

        if (!validateRecordingName(name)) {
            cw.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        return true;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && recordingsPath.toFile().isDirectory();
    }

}
