package es.andrewazor.containertest.commands.internal;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.jmc.serialization.SerializableOptionDescriptor;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListRecordingOptionsCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject ListRecordingOptionsCommand(ClientWriter cw) {
        this.cw = cw;
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
        cw.println("Available recording options:");
        getService().getAvailableRecordingOptions().entrySet().forEach(this::printOptions);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            Map<String, IOptionDescriptor<?>> origOptions = getService().getAvailableRecordingOptions();
            Map<String, SerializableOptionDescriptor> options = new HashMap<>(origOptions.size());
            for (Map.Entry<String, IOptionDescriptor<?>> entry : origOptions.entrySet()) {
                options.put(entry.getKey(), new SerializableOptionDescriptor(entry.getValue()));
            }
            return new MapOutput<>(options);
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

    private void printOptions(Map.Entry<String, IOptionDescriptor<?>> entry) {
        cw.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
    }
}
