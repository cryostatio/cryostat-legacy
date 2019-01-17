package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.dockertest.JMCConnection;

class EventOptionsBuilder {

    private final boolean isV2;

    EventOptionsBuilder(JMCConnection connection) {
        this.isV2 = FlightRecorderServiceV2.isAvailable(connection.getHandle());
    }


    IConstrainedMap<EventOptionID> build() {
        return null;
    }
}
