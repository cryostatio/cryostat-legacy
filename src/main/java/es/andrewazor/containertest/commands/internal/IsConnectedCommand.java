package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class IsConnectedCommand implements ConnectionListener, SerializableCommand {

    private final ClientWriter cw;
    private JMCConnection connection;

    @Inject
    IsConnectedCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "is-connected";
    }

    @Override
    public void connectionChanged(JMCConnection connection) {
        this.connection = connection;
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println("\t" + (connection != null ? String.format("%s:%d", connection.getHost(), connection.getPort()) : "Disconnected"));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        if (connection != null) {
            return new StringOutput(String.format("%s:%d", connection.getHost(), connection.getPort()));
        } else {
            return new StringOutput("false");
        }
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
    public boolean isAvailable() {
        return true;
    }

}