package es.andrewazor.dockertest.commands.internal;

import static es.andrewazor.dockertest.commands.internal.EventOptionsBuilder.Option;

import java.time.LocalDate;
import java.time.LocalTime;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.JMCConnection;

class DumpCommand extends AbstractCommand {
    DumpCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "dump";
    }

    /**
     * Three args expected.
     * First argument is recording name, second argument is recording length in seconds.
     * Third argument is comma-separated names of event types to enable.
     */
    // TODO better syntax for specifying events and options, so that options other than "enabled"
    // can be specified (ex. threshold, duration)
    @Override
    public void execute(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Expected three arguments: recording name, recording length, and event types.");
        }
        String name = args[0];
        int seconds = Integer.parseInt(args[1]);
        String[] events = args[2].split(",");

        for (IRecordingDescriptor recording : service.getAvailableRecordings()) {
            if (recording.getName().equals(name)) {
                System.out.println(String.format("Recording with name %s already exists", name));
                return;
            }
        }

        IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(service)
            .name(name)
            .duration(1000 * seconds)
            .build();
        IRecordingDescriptor descriptor = service.start(recordingOptions, null);

        EventOptionsBuilder builder = new EventOptionsBuilder(connection);
        enableEvents(builder, events);

        service.updateEventOptions(descriptor, builder.build());
    }

    private void enableEvents(EventOptionsBuilder builder, String[] events) throws QuantityConversionException {
        for (String event : events) {
            if (event.equals("socketWrite")) {
                builder.socketWrite(Option.ENABLED, Boolean.TRUE);
            } else if (event.equals("socketRead")) {
                builder.socketRead(Option.ENABLED, Boolean.TRUE);
            } else if (event.equals("highCpu")) {
                builder.highCpu(Option.ENABLED, Boolean.TRUE);
            }
        }
    }
}
