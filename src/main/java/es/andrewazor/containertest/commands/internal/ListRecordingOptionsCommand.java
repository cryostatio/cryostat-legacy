package es.andrewazor.containertest.commands.internal;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListRecordingOptionsCommand extends AbstractConnectedCommand {

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
