package es.andrewazor.containertest;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceFactory;

public class JMCConnection {

    private final RJMXConnection rjmxConnection;
    private final IConnectionHandle handle;
    private final IFlightRecorderService service;

    public JMCConnection(RJMXConnection rjmxConnection, IConnectionHandle handle) throws Exception {
        this.rjmxConnection = rjmxConnection;
        this.handle = handle;
        this.service = new FlightRecorderServiceFactory().getServiceInstance(handle);
    }

    public IConnectionHandle getHandle() {
        return this.handle;
    }

    public IFlightRecorderService getService() {
        return this.service;
    }

    public long getApproximateServerTime() {
        return rjmxConnection.getApproximateServerTime(System.currentTimeMillis());
    }
}
