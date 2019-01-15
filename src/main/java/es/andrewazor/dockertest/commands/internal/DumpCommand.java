package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.commands.Command;

class DumpCommand implements Command {
    @Override
    public String getName() {
        return "dump";
    }

    @Override
    public void execute(IFlightRecorderService service, String[] args) throws Exception {
        IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(service)
            .toDisk(true)
            .duration(10000)
            .build();
        service.start(recordingOptions, null);
    }
}
