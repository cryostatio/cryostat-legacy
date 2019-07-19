package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

abstract class AbstractRecordingCommand extends AbstractConnectedCommand {

    private static final Pattern ALL_EVENTS_PATTERN = Pattern.compile("^ALL$", Pattern.MULTILINE);
    private static final Pattern EVENTS_PATTERN = Pattern.compile("([\\w\\.\\$]+):([\\w]+)=([\\w\\d\\.]+)");

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
        if (ALL_EVENTS_PATTERN.matcher(events).matches()) {
            return enableAllEvents();
        }

        return enableSelectedEvents(events);
    }

    protected IConstrainedMap<EventOptionID> enableAllEvents() throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        for (IEventTypeInfo eventTypeInfo : getService().getAvailableEventTypes()) {
            builder.addEvent(eventTypeInfo.getEventTypeID().getFullKey(), "enabled", "true");
        }

        return builder.build();
    }

    protected IConstrainedMap<EventOptionID> enableSelectedEvents(String events) throws Exception {
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
        if (!ALL_EVENTS_PATTERN.matcher(events).matches() && !EVENTS_PATTERN.matcher(events).find()) {
            cw.println(String.format("%s is an invalid events pattern", events));
            return false;
        }

        return true;
    }
}
