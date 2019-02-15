package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.JMCConnection;

public class ExitCommand extends AbstractCommand {

    public static final String NAME = "exit";

    ExitCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) {
        if (connection != null) {
            connection.getRecordingExporter().stop();
        }
    };

}