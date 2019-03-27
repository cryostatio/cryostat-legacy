package es.andrewazor.containertest.commands.internal;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import es.andrewazor.containertest.ConnectionListener;

@Singleton
class DisconnectCommand extends AbstractConnectedCommand {

    private final Lazy<Set<ConnectionListener>> connectionListeners;

    @Inject DisconnectCommand(Lazy<Set<ConnectionListener>> connectionListeners) {
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
        getConnection().disconnect();
        connectionListeners.get().forEach(listener -> listener.connectionChanged(null));
    }

}