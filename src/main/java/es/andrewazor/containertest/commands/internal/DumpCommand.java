package es.andrewazor.containertest.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.JMCConnection;

class DumpCommand extends AbstractCommand {

    private static final Pattern EVENTS_PATTERN = Pattern.compile("([\\w\\.]+):([\\w]+)=([\\w\\d\\.]+)");

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
     * Third argument is comma-separated event options list, ex. jdk.SocketWrite:enabled=true,com.foo:ratio=95.2
     */
    @Override
    public void execute(String[] args) throws Exception {
        validateConnection();
        String name = args[0];
        int seconds = Integer.parseInt(args[1]);
        String events = args[2];

        if (service.getAvailableRecordings().stream().anyMatch(recording -> recording.getName().equals(name))) {
            System.out.println(String.format("Recording with name %s already exists", name));
            return;
        }

        IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(service)
            .name(name)
            .duration(1000 * seconds)
            .build();
        this.connection.getRecordingExporter().addRecording(service.start(recordingOptions, enableEvents(events)));
    }

    private IConstrainedMap<EventOptionID> enableEvents(String events) throws Exception {
        EventOptionsBuilder builder = new EventOptionsBuilder(this.connection);

        Matcher matcher = EVENTS_PATTERN.matcher(events);
        while (matcher.find()) {
            String eventTypeId = matcher.group(1);
            String option = matcher.group(2);
            String value = matcher.group(3);

            builder.addEvent(eventTypeId, option, value);
        }

        return builder.build();
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 3) {
            System.out.println("Expected three arguments: recording name, recording length, and event types.");
            return false;
        }

        String name = args[0];
        String seconds = args[1];
        String events = args[2];

        if (!name.matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        if (!seconds.matches("\\d+")) {
            System.out.println(String.format("%s is an invalid recording length", seconds));
            return false;
        }

        // TODO better validation of entire events string (not just looking for one acceptable setting)
        if (!EVENTS_PATTERN.matcher(events).find()) {
            System.out.println(String.format("%s is an invalid events pattern", events));
            return false;
        }

        return true;
    }
}