package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.JMCConnection;

class HostnameCommand extends AbstractCommand {

    static final String NAME = "hostname";

    HostnameCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) throws Exception {
        validateConnection();
        System.out.println(String.format("\t%s", connection.getRecordingExporter().getHostName()));
    }
}