package es.andrewazor.dockertest.commands.internal;

import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.dockertest.JMCConnection;

class EventOptionsBuilder {

    private static Map<IEventTypeID, Map<String, IOptionDescriptor<?>>> KNOWN_TYPES = null;
    private static Map<String, IEventTypeID> EVENT_IDS = null;

    private final boolean isV2;
    private final IMutableConstrainedMap<EventOptionID> map;

    EventOptionsBuilder(JMCConnection connection) throws FlightRecorderException {
        this.isV2 = FlightRecorderServiceV2.isAvailable(connection.getHandle());
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();

        if (!isV2) {
            System.out.println("Flight Recorder V1 is not yet supported");
        }

        if (KNOWN_TYPES == null) {
            KNOWN_TYPES = new HashMap<>();
            EVENT_IDS = new HashMap<>();

            for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
                EVENT_IDS.put(eventTypeInfo.getEventTypeID().getFullKey(), eventTypeInfo.getEventTypeID());
                KNOWN_TYPES.putIfAbsent(eventTypeInfo.getEventTypeID(), new HashMap<>(eventTypeInfo.getOptionDescriptors()));
            }
        }
    }

    EventOptionsBuilder addEvent(String typeId, String option, String value) throws Exception {
        if (!EVENT_IDS.containsKey(typeId)) {
            throw new EventTypeException(typeId);
        }
        Map<String, IOptionDescriptor<?>> optionDescriptors = KNOWN_TYPES.get(EVENT_IDS.get(typeId));
        if (!optionDescriptors.containsKey(option)) {
            throw new EventOptionException(typeId, option);
        }
        // TODO use OptionDescriptor.getConstraint().validate() to validate value
        this.map.put(new EventOptionID(EVENT_IDS.get(typeId), option), optionDescriptors.get(option).getConstraint().parseInteractive(value));

        return this;
    }

    IConstrainedMap<EventOptionID> build() {
        if (!isV2) {
            return null;
        }
        return map;
    }

    static class EventTypeException extends Exception {
        EventTypeException(String eventType) {
            super(String.format("Unknown event type \"%s\"", eventType));
        }
    }

    static class EventOptionException extends Exception {
        EventOptionException(String eventType, String option) {
            super(String.format("Unknown option \"%s\" for event \"%s\"", option, eventType));
        }
    }
}
