package es.andrewazor.dockertest.commands.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.JMCConnection;

class DownloadCommand extends AbstractCommand {
    DownloadCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "download";
    }

    /**
     * Two args expected. First argument is recordingName, second is save path relative to user home.
     */
    @Override
    public void execute(String[] args) throws Exception {
        String recordingName = args[0];
        String saveName = args[1];
        Path savePath = Paths.get("recordings", saveName);

        if (savePath.toFile().exists()) {
            System.out.println(String.format("Save file %s already exists, canceling download", savePath));
            return;
        }

        IRecordingDescriptor descriptor = null;
        for (IRecordingDescriptor recording : service.getAvailableRecordings()) {
            if (recording.getName().equals(recordingName)) {
                descriptor = recording;
                break;
            }
        }

        if (descriptor == null) {
            System.out.println(String.format("\tCould not locate recording named \"%s\"", recordingName));
            return;
        }

        System.out.println(String.format("\tDownloading recording \"%s\" to \"%s\" ...", recordingName, savePath.toString()));

        Files.copy(service.openStream(descriptor, false), savePath);
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            System.out.println(String.format("%s expects two arguments (recording name, save path)", getName()));
            return false;
        }
        String recordingName = args[0];
        String savePath = args[1];

        if (!recordingName.matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", recordingName));
            return false;
        }

        // TODO validate savePath

        return true;
    }
}
