package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

class PingCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject
    PingCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "ping";
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

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("pong");
    }

    @Override
    public Output serializableExecute(String[] args) {
        return new SuccessOutput();
    }
}


