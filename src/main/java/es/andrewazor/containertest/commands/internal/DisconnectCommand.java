package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.CommandRegistryFactory;

class DisconnectCommand extends AbstractCommand {

    @Override
    public String getName() {
        return "disconnect";
    }

    DisconnectCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) throws Exception {
        connection.getRecordingExporter().stop();
        CommandRegistryFactory.getInstance().setConnection(null);
    }

}