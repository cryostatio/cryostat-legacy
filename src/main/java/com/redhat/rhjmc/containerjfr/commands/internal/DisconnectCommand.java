package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

@Singleton
class DisconnectCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final Lazy<Set<ConnectionListener>> connectionListeners;
    private final ClientWriter cw;

    @Inject DisconnectCommand(Lazy<Set<ConnectionListener>> connectionListeners, ClientWriter cw) {
        this.connectionListeners = connectionListeners;
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "disconnect";
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        disconnectPreviousConnection();
        connectionListeners.get().forEach(listener -> listener.connectionChanged(null));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        disconnectPreviousConnection();
        connectionListeners.get().forEach(listener -> listener.connectionChanged(null));
        return new SuccessOutput();
    }

    private void disconnectPreviousConnection() {
        try {
            getConnection().disconnect();
        } catch (Exception e) {
            cw.println("No active connection");
        }
    }

}