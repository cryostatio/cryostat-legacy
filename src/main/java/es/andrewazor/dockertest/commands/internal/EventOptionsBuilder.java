package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.ConfigurationToolkit;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.dockertest.JMCConnection;

class EventOptionsBuilder {

    private final boolean isV2;
    private final IMutableConstrainedMap<EventOptionID> map;

    EventOptionsBuilder(JMCConnection connection) {
        this.isV2 = FlightRecorderServiceV2.isAvailable(connection.getHandle());
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();

        if (!isV2) {
            System.out.println("Flight Recorder V1 is not yet supported");
        }
    }

    void addEvent(String typeId, String option, Object value) throws QuantityConversionException {
        this.map.put(new EventOptionID(ConfigurationToolkit.createEventTypeID(typeId), option), value);
    }

    IConstrainedMap<EventOptionID> build() {
        if (!isV2) {
            return null;
        }
        return map;
    }
}
