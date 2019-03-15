package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.RecordingExporter;

@Singleton
class SnapshotCommand extends AbstractRecordingCommand {

    private final RecordingExporter exporter;

    @Inject SnapshotCommand(RecordingExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "snapshot";
    }

    @Override
    public void execute(String[] args) throws Exception {
        exporter.addSnapshot(getService().getSnapshotRecording());
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }
}
