package es.andrewazor.dockertest;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceFactory;

public class JMCConnection {

    private final IConnectionHandle handle;
    private final IFlightRecorderService service;

    public JMCConnection(IConnectionHandle handle) throws Exception {
        this.handle = handle;
        this.service = new FlightRecorderServiceFactory().getServiceInstance(handle);
    }

    public IConnectionHandle getHandle() {
        return this.handle;
    }

    public IFlightRecorderService getService() {
        return this.service;
    }
}
