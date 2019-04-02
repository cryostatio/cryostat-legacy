package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class DeleteCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject DeleteCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "delete";
    }

    /**
     * One arg expected. Deletes recordings in target JVM by recording name.
     */
    @Override
    public void execute(String[] args) throws Exception {
        final String recordingName = args[0];
        for (IRecordingDescriptor recording : getService().getAvailableRecordings()) {
            if (recording.getName().equals(recordingName)) {
                getService().close(recording);
                return;
            }
        }
        cw.println(String.format("No recording with name \"%s\" found", recordingName));
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 1;
    }
}
