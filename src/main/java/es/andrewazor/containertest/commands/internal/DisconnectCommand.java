package es.andrewazor.containertest.commands.internal;

import java.util.Set;

import javax.inject.Inject;

import es.andrewazor.containertest.ConnectionListener;
import es.andrewazor.containertest.commands.Command;

class DisconnectCommand implements Command {

    private final Set<ConnectionListener> connectionListeners;

    @Inject DisconnectCommand(Set<ConnectionListener> connectionListeners) {
        this.connectionListeners = connectionListeners;
    }

    @Override
    public String getName() {
        return "disconnect";
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) throws Exception {
        connectionListeners.forEach(listener -> listener.connectionChanged(null));
    }

}