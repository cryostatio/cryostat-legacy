package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;

abstract class AbstractCommand implements Command {
    protected final JMCConnection connection;
    protected final IFlightRecorderService service;
    protected final IConnectionHandle handle;

    protected AbstractCommand(JMCConnection connection) {
        this.connection = connection;
        if (connection != null) {
            this.service = connection.getService();
            this.handle = connection.getHandle();
        } else {
            this.service = null;
            this.handle = null;
        }
    }

    protected void validateConnection() {
        if (connection == null) {
            throw new RuntimeException("disconnected");
        }
    }
}
