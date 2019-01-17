package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.dockertest.JMCConnection;

class EventOptionsBuilder {

    private final boolean isV2;
    private final IConstrainedMap<EventOptionID> map;

    EventOptionsBuilder(JMCConnection connection) {
        this.isV2 = FlightRecorderServiceV2.isAvailable(connection.getHandle());
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();
    }

    IConstrainedMap<EventOptionID> build() {
        return map;
    }
}
