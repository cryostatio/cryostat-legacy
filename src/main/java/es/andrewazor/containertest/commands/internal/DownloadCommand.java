package es.andrewazor.containertest.commands.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;

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
        Path savePath = Paths.get("recordings", args[1]);

        System.out.println(String.format("\tDownloading recording \"%s\" to \"%s\" ...", recordingName, savePath.toString()));

        IRecordingDescriptor recording = getRecordingByName(recordingName);
        if (recording == null) {
            System.out.println(String.format("\tCould not locate recording named \"%s\"", recordingName));
            return;
        }

        Files.copy(service.openStream(recording, false), savePath);
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            System.out.println(String.format("%s expects two arguments (recording name, save path)", getName()));
            return false;
        }
        String recordingName = args[0];
        String saveName = args[1];

        if (!recordingName.matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", recordingName));
            return false;
        }

        Path savePath = Paths.get("recordings", saveName);
        if (savePath.toFile().exists()) {
            System.out.println(String.format("Save file %s already exists, canceling download", savePath));
            return false;
        }

        return true;
    }

    private IRecordingDescriptor getRecordingByName(String recordingName) throws Exception {
        for (IRecordingDescriptor recording : service.getAvailableRecordings()) {
            if (recording.getName().equals(recordingName)) {
                return recording;
            }
        }
        return null;
    }

}
