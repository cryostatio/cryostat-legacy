package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.RecordingExporter;
import es.andrewazor.containertest.jmc.CopyRecordingDescriptor;

@Singleton
class SnapshotCommand extends AbstractRecordingCommand {

    private final RecordingExporter exporter;

    @Inject SnapshotCommand(RecordingExporter exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory) {
        super(eventOptionsBuilderFactory);
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "snapshot";
    }

    @Override
    public void execute(String[] args) throws Exception {
        IRecordingDescriptor descriptor = getService().getSnapshotRecording();

        String rename = String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());
        System.out.println(String.format("Latest snapshot: \"%s\"", rename));

        RecordingOptionsBuilder recordingOptionsBuilder = new RecordingOptionsBuilder(getService());
            recordingOptionsBuilder.name(rename);

        getService().updateRecordingOptions(descriptor, recordingOptionsBuilder.build());
        exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    private static class RenamedSnapshotDescriptor extends CopyRecordingDescriptor {
        private final String rename;

        RenamedSnapshotDescriptor(String rename, IRecordingDescriptor original) {
            super(original);
            this.rename = rename;
        }

        @Override
        public String getName() {
            return rename;
        }
    }

}
