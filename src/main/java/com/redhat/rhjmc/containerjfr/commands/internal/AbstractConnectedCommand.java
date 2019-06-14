package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.Optional;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.net.JMCConnection;

abstract class AbstractConnectedCommand implements Command, ConnectionListener {

    protected JMCConnection connection;

    @Override
    public final void connectionChanged(JMCConnection connection) {
        this.connection = connection;
    }

    @Override
    public boolean isAvailable() {
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

    protected boolean validateRecordingName(String name) {
        return name.matches("[\\w-_]+");
    }

    protected Optional<IRecordingDescriptor> getDescriptorByName(String name)
            throws FlightRecorderException, JMXConnectionException {
        return getService().getAvailableRecordings().stream()
            .filter(recording -> recording.getName().equals(name))
            .findFirst();
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