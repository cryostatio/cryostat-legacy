package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;

abstract class AbstractConnectedCommand implements Command, ConnectionListener {

    protected JMCConnection connection;

    @Override
    public final void connectionChanged(JMCConnection connection) {
        this.connection = connection;
    }

    protected JMCConnection getConnection() {
        validateConnection();
        return this.connection;
    }

    protected IFlightRecorderService getService() {
        validateConnection();
        return this.connection.getService();
    }

    private void validateConnection() {
        if (this.connection == null) {
            throw new RuntimeException("disconnected");
        }
    }
}