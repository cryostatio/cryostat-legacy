package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.ConfigurationToolkit;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceV2;

import es.andrewazor.dockertest.JMCConnection;

class EventOptionsBuilder {

     enum Option {
        ENABLED("enabled"),
        THRESHOLD("threshold"),
        STACK_TRACE("stackTrace"),
        ;

        private final String key;

        Option(String key) {
            this.key = key;
        }

        String getKey() {
            return key;
        }
    }

    private final boolean isV2;
    private final IMutableConstrainedMap<EventOptionID> map;

    EventOptionsBuilder(JMCConnection connection) {
        this.isV2 = FlightRecorderServiceV2.isAvailable(connection.getHandle());
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();

        if (!isV2) {
            System.out.println("Flight Recorder V1 is not yet supported");
        }
    }

    EventOptionsBuilder socketWrite(Option key, Object value) throws QuantityConversionException {
        addEvent(JdkTypeIDs.SOCKET_WRITE, key, value);
        return this;
    }

    EventOptionsBuilder socketRead(Option key, Object value) throws QuantityConversionException {
        addEvent(JdkTypeIDs.SOCKET_READ, key, value);
        return this;
    }

    EventOptionsBuilder highCpu(Option key, Object value) throws QuantityConversionException {
        addEvent(JdkTypeIDs.CPU_LOAD, key, value);
        return this;
    }

    private void addEvent(String typeId, Option option, Object value) throws QuantityConversionException {
        this.map.put(new EventOptionID(ConfigurationToolkit.createEventTypeID(typeId), option.getKey()), value);
    }

    IConstrainedMap<EventOptionID> build() {
        if (!isV2) {
            return null;
        }
        return map;
    }
}
