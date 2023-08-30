/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.recordings;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.tui.ClientWriter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class EventOptionsBuilder {

    private final boolean isV2;
    private final IMutableConstrainedMap<EventOptionID> map;
    private Map<IEventTypeID, Map<String, IOptionDescriptor<?>>> knownTypes;
    private Map<String, IEventTypeID> eventIds;

    // Testing only
    EventOptionsBuilder(ClientWriter cw, JFRConnection connection, Supplier<Boolean> v2)
            throws Exception {
        this.isV2 = v2.get();
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();
        knownTypes = new HashMap<>();
        eventIds = new HashMap<>();

        if (!isV2) {
            cw.println("Flight Recorder V1 is not yet supported");
        }

        for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
            eventIds.put(
                    eventTypeInfo.getEventTypeID().getFullKey(), eventTypeInfo.getEventTypeID());
            knownTypes.putIfAbsent(
                    eventTypeInfo.getEventTypeID(),
                    new HashMap<>(eventTypeInfo.getOptionDescriptors()));
        }
    }

    public EventOptionsBuilder addEvent(String typeId, String option, String value)
            throws Exception {
        if (!eventIds.containsKey(typeId)) {
            throw new EventTypeException(typeId);
        }
        Map<String, IOptionDescriptor<?>> optionDescriptors = knownTypes.get(eventIds.get(typeId));
        if (!optionDescriptors.containsKey(option)) {
            throw new EventOptionException(typeId, option);
        }
        IConstraint<?> constraint = optionDescriptors.get(option).getConstraint();
        Object parsedValue = constraint.parseInteractive(value);
        constraint.validate(capture(parsedValue));
        this.map.put(new EventOptionID(eventIds.get(typeId), option), parsedValue);

        return this;
    }

    static <T, V> V capture(T t) {
        // TODO clean up this generics hack
        return (V) t;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Field is never mutated")
    public IConstrainedMap<EventOptionID> build() {
        if (!isV2) {
            return null;
        }
        return map;
    }

    public static class EventTypeException extends Exception {
        EventTypeException(String eventType) {
            super(String.format("Unknown event type \"%s\"", eventType));
        }
    }

    static class EventOptionException extends Exception {
        EventOptionException(String eventType, String option) {
            super(String.format("Unknown option \"%s\" for event \"%s\"", option, eventType));
        }
    }

    public static class Factory {
        private final ClientWriter cw;

        public Factory(ClientWriter cw) {
            this.cw = cw;
        }

        public EventOptionsBuilder create(JFRConnection connection) throws Exception {
            IConnectionHandle handle = connection.getHandle();
            return new EventOptionsBuilder(
                    cw, connection, () -> FlightRecorderServiceV2.isAvailable(handle));
        }
    }
}
