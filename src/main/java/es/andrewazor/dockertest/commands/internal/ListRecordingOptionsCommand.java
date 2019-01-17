package es.andrewazor.dockertest.commands.internal;

import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import es.andrewazor.dockertest.JMCConnection;

class ListRecordingOptionsCommand extends AbstractCommand {
    ListRecordingOptionsCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "list-recording-options";
    }

    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available recording options:");
        Map<String, IOptionDescriptor<?>> options = service.getAvailableRecordingOptions();
        for (Map.Entry<String, IOptionDescriptor<?>> entry : options.entrySet()) {
            System.out.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
        }
    }
}
