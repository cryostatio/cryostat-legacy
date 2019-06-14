package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.jmc.CopyRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

@Singleton
class SnapshotCommand extends AbstractRecordingCommand implements SerializableCommand {

    private final RecordingExporter exporter;

    @Inject
    SnapshotCommand(ClientWriter cw, RecordingExporter exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        super(cw, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
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
        cw.println(String.format("Latest snapshot: \"%s\"", rename));

        RecordingOptionsBuilder recordingOptionsBuilder = recordingOptionsBuilderFactory.create(getService());
            recordingOptionsBuilder.name(rename);

        getService().updateRecordingOptions(descriptor, recordingOptionsBuilder.build());
        exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            IRecordingDescriptor descriptor = getService().getSnapshotRecording();

            String rename = String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());

            RecordingOptionsBuilder recordingOptionsBuilder = recordingOptionsBuilderFactory.create(getService());
                recordingOptionsBuilder.name(rename);

            getService().updateRecordingOptions(descriptor, recordingOptionsBuilder.build());
            exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));

            return new StringOutput(rename);
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
