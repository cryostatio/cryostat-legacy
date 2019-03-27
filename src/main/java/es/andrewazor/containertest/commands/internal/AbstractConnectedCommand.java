package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.Command;

abstract class AbstractConnectedCommand implements Command, ConnectionListener {

    protected JMCConnection connection;

    @Override
    public void connectionChanged(JMCConnection connection) {
        this.connection = connection;
    }

    @Override
    public final boolean isAvailable() {
        return this.connection != null;
    }

    protected JMCConnection getConnection() throws JMXConnectionException {
        validateConnection();
        return this.connection;
    }

    protected IFlightRecorderService getService() throws JMXConnectionException {
        validateConnection();
        return this.connection.getService();
    }

    private void validateConnection() throws JMXConnectionException {
        if (this.connection == null) {
            throw new JMXConnectionException();
        }
    }

    @SuppressWarnings("serial")
    static class JMXConnectionException extends Exception {
        JMXConnectionException() {
            super("No active JMX connection");
        }
    }
}