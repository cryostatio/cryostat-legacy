package es.andrewazor.containertest;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.internal.FlightRecorderServiceFactory;

import es.andrewazor.containertest.commands.internal.RecordingExporter;

public class JMCConnection {

    private final IConnectionHandle handle;
    private final IFlightRecorderService service;
    private final RecordingExporter exporter;

    public JMCConnection(IConnectionHandle handle) throws Exception {
        this.handle = handle;
        this.service = new FlightRecorderServiceFactory().getServiceInstance(handle);
        this.exporter = new RecordingExporter(service);
    }

    public IConnectionHandle getHandle() {
        return this.handle;
    }

    public IFlightRecorderService getService() {
        return this.service;
    }

    public RecordingExporter getRecordingExporter() {
        return this.exporter;
    }
}
