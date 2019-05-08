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
    private boolean connected = false;

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
        this.connected = connection != null;
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println(connected ? "Connected" : "Disconnected");
    }

    @Override
    public Output serializableExecute(String[] args) {
        try {
            return new StringOutput(String.valueOf(connected));
        } catch (Exception e) {
            return new ExceptionOutput(e);
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