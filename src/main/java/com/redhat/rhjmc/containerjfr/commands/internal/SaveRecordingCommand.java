package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class SaveRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;

    @Inject
    SaveRecordingCommand(ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.fs = fs;
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
            cw.println(String.format("Recording saved as \"%s\"", saveRecording(descriptor.get())));
        } else {
            cw.println(String.format("Recording with name \"%s\" not found", name));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        String name = args[0];

        try {
            Optional<IRecordingDescriptor> descriptor = getDescriptorByName(name);
            if (descriptor.isPresent()) {
                return new StringOutput(saveRecording(descriptor.get()));
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
        return super.isAvailable() && fs.isDirectory(recordingsPath);
    }

    private String saveRecording(IRecordingDescriptor descriptor)
            throws IOException, FlightRecorderException, JMXConnectionException {
        String recordingName = descriptor.getName();
        if (!recordingName.endsWith(".jfr")) {
            recordingName += ".jfr";
        }
        String targetName = getConnection().getHost().replaceAll("[\\._]+", "-");
        String destination = String.format("%s_%s", targetName, recordingName);
        fs.copy(
            getService().openStream(descriptor, false),
            recordingsPath.resolve(destination),
            StandardCopyOption.REPLACE_EXISTING
        );
        return destination;
    }

}
