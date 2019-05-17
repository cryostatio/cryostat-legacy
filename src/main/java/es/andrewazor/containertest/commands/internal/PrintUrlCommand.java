package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class PrintUrlCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final RecordingExporter exporter;

    @Inject PrintUrlCommand(ClientWriter cw, RecordingExporter exporter) {
        this.cw = cw;
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "url";
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println(exporter.getHostUrl().toString());
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new StringOutput(exporter.getHostUrl().toString());
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

}