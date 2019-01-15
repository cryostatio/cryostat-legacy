package es.andrewazor.dockertest.commands.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.commands.Command;

class DownloadCommand implements Command {
    @Override
    public String getName() {
        return "download";
    }

    @Override
    public void execute(IFlightRecorderService service, String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println(String.format("%s expects two arguments (recording name, save path)", getName()));
        }

        String recordingName = args[0];
        String savePath = args[1];

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

        System.out.println(String.format("\tDownloading recording \"%s\" to \"%s\" ...", recordingName, savePath));

        Files.copy(service.openStream(descriptor, false), Paths.get(savePath));
    }
}
