package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.JMCConnection;
import es.andrewazor.dockertest.commands.Command;

abstract class AbstractCommand implements Command {
    protected final JMCConnection connection;
    protected final IFlightRecorderService service;
    protected final IConnectionHandle handle;

    protected AbstractCommand(JMCConnection connection) {
        this.connection = connection;
        this.service = connection.getService();
        this.handle = connection.getHandle();
    }
}
