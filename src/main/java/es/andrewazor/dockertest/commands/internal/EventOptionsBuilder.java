package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

class EventOptionsBuilder {

    private final boolean isV2;

    EventOptionsBuilder(IConnectionHandle handle) {
        this.isV2 = FlightRecorderServiceV2.isAvailable(handle);
    }


    IConstrainedMap<EventOptionID> build() {
        return null;
    }
}
