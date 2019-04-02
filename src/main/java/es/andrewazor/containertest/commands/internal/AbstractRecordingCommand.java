package es.andrewazor.containertest.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;

import es.andrewazor.containertest.tui.ClientWriter;

abstract class AbstractRecordingCommand extends AbstractConnectedCommand {

    private static final Pattern EVENTS_PATTERN = Pattern.compile("([\\w\\.]+):([\\w]+)=([\\w\\d\\.]+)");

    protected final ClientWriter cw;
    protected final EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    protected final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    protected AbstractRecordingCommand(ClientWriter cw, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        this.cw = cw;
        this.eventOptionsBuilderFactory = eventOptionsBuilderFactory;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
    }

    protected IConstrainedMap<EventOptionID> enableEvents(String events) throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        Matcher matcher = EVENTS_PATTERN.matcher(events);
        while (matcher.find()) {
            String eventTypeId = matcher.group(1);
            String option = matcher.group(2);
            String value = matcher.group(3);

            builder.addEvent(eventTypeId, option, value);
        }

        return builder.build();
    }

    protected boolean validateEvents(String events) {
        // TODO better validation of entire events string (not just looking for one acceptable setting)
        if (!EVENTS_PATTERN.matcher(events).find()) {
            cw.println(String.format("%s is an invalid events pattern", events));
            return false;
        }

        return true;
    }
}
