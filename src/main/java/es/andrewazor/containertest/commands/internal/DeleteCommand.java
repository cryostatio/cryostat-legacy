package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@Singleton
class DeleteCommand extends AbstractConnectedCommand {

    @Inject DeleteCommand() { }

    @Override
    public String getName() {
        return "delete";
    }

    /**
     * One arg expected. Deletes recordings in target JVM by recording name.
     * TODO: handle snapshots, since those all have the same name and must be differentiated by ID
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
        System.out.println(String.format("No recording with name \"%s\" found", recordingName));
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 1;
    }
}
