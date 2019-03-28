package es.andrewazor.containertest.commands.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.containertest.JMCConnection;

class EventOptionsBuilder {

    private static Map<IEventTypeID, Map<String, IOptionDescriptor<?>>> KNOWN_TYPES = null;
    private static Map<String, IEventTypeID> EVENT_IDS = null;

    private final boolean isV2;
    private final IMutableConstrainedMap<EventOptionID> map;

    private EventOptionsBuilder(JMCConnection connection) throws FlightRecorderException {
        this(connection, () -> FlightRecorderServiceV2.isAvailable(connection.getHandle()));
    }

    EventOptionsBuilder(JMCConnection connection, Supplier<Boolean> v2) throws FlightRecorderException {
        this.isV2 = v2.get();
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
        IConstraint<?> constraint = optionDescriptors.get(option).getConstraint();
        Object parsedValue = constraint.parseInteractive(value);
        constraint.validate(capture(parsedValue));
        this.map.put(new EventOptionID(EVENT_IDS.get(typeId), option), parsedValue);

        return this;
    }

    private static <T, V> V capture(T t) {
        // TODO clean up this generics hack
        return (V) t;
    }

    IConstrainedMap<EventOptionID> build() {
        if (!isV2) {
            return null;
        }
        return map;
    }

    @SuppressWarnings("serial")
    static class EventTypeException extends Exception {
        EventTypeException(String eventType) {
            super(String.format("Unknown event type \"%s\"", eventType));
        }
    }

    @SuppressWarnings("serial")
    static class EventOptionException extends Exception {
        EventOptionException(String eventType, String option) {
            super(String.format("Unknown option \"%s\" for event \"%s\"", option, eventType));
        }
    }

    public static class Factory {
        public EventOptionsBuilder create(JMCConnection connection) throws FlightRecorderException {
            return new EventOptionsBuilder(connection);
        }
    }
}
