package es.andrewazor.containertest.commands.internal;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class DisconnectCommand extends AbstractConnectedCommand {

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
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) throws Exception {
        disconnectPreviousConnection();
        connectionListeners.get().forEach(listener -> listener.connectionChanged(null));
    }

    private void disconnectPreviousConnection() {
        try {
            getConnection().disconnect();
        } catch (Exception e) {
            cw.println("No active connection");
        }
    }

}