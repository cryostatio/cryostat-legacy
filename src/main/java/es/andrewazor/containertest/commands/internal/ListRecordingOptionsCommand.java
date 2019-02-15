package es.andrewazor.containertest.commands.internal;

import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import es.andrewazor.containertest.JMCConnection;

class ListRecordingOptionsCommand extends AbstractCommand {
    ListRecordingOptionsCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "list-recording-options";
    }

    /**
     * No args expected. Prints list of available recording options in target JVM.
     */
    @Override
    public void execute(String[] args) throws Exception {
        validateConnection();
        System.out.println("Available recording options:");
        service.getAvailableRecordingOptions().entrySet().forEach(this::printOptions);
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }

    private void printOptions(Map.Entry<String, IOptionDescriptor<?>> entry) {
        System.out.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
    }
}
