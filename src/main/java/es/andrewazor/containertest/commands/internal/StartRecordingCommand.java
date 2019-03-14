package es.andrewazor.containertest.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import es.andrewazor.containertest.RecordingExporter;

@Singleton
class StartRecordingCommand extends AbstractConnectedCommand {

    private static final Pattern EVENTS_PATTERN = Pattern.compile("([\\w\\.]+):([\\w]+)=([\\w\\d\\.]+)");

    private final RecordingExporter exporter;

    @Inject StartRecordingCommand(RecordingExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "start";
    }

    /**
     * Two args expected.
     * First argument is recording name, second argument is recording length in seconds.
     * Second argument is comma-separated event options list, ex. jdk.SocketWrite:enabled=true,com.foo:ratio=95.2
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        String events = args[1];

        if (getService().getAvailableRecordings().stream().anyMatch(recording -> recording.getName().equals(name))) {
            System.out.println(String.format("Recording with name \"%s\" already exists", name));
            return;
        }

        IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(getService())
            .name(name)
            .build();
        this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
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
        if (args.length != 2) {
            System.out.println("Expected two arguments: recording name and event types.");
            return false;
        }

        String name = args[0];
        String events = args[1];

        if (!name.matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", name));
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
