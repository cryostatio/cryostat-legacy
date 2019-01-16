package es.andrewazor.dockertest.commands.internal;

import java.time.LocalDate;
import java.time.LocalTime;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.commands.Command;

class DumpCommand extends AbstractCommand {
    DumpCommand(IFlightRecorderService service) {
        super(service);
    }

    @Override
    public String getName() {
        return "dump";
    }

    /**
     * First argument is recording name, second argument is recording length in seconds
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        int seconds = Integer.parseInt(args[1]);
        IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(service)
            .name(name)
            .duration(1000 * seconds)
            .build();
        service.start(recordingOptions, null);
    }
}
