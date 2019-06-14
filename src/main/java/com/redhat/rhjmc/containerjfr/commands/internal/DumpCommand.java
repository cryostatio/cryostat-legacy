package com.redhat.rhjmc.containerjfr.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IConstrainedMap;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.net.RecordingExporter;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

@Singleton
class DumpCommand extends AbstractRecordingCommand implements SerializableCommand {

    private final RecordingExporter exporter;

    @Inject
    DumpCommand(ClientWriter cw, RecordingExporter exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        super(cw, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "dump";
    }

    /**
     * Three args expected.
     * First argument is recording name, second argument is recording length in seconds.
     * Third argument is comma-separated event options list, ex. jdk.SocketWrite:enabled=true,com.foo:ratio=95.2
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        int seconds = Integer.parseInt(args[1]);
        String events = args[2];

        if (getDescriptorByName(name).isPresent()) {
            cw.println(String.format("Recording with name \"%s\" already exists", name));
            return;
        }

        IConstrainedMap<String> recordingOptions = recordingOptionsBuilderFactory.create(getService())
            .name(name)
            .duration(1000 * seconds)
            .build();
        this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];
            int seconds = Integer.parseInt(args[1]);
            String events = args[2];

            if (getDescriptorByName(name).isPresent()) {
                return new FailureOutput(String.format("Recording with name \"%s\" already exists", name));
            }

            IConstrainedMap<String> recordingOptions = recordingOptionsBuilderFactory.create(getService())
                .name(name)
                .duration(1000 * seconds)
                .build();
            this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
            return new SuccessOutput();
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 3) {
            cw.println("Expected three arguments: recording name, recording length, and event types");
            return false;
        }

        String name = args[0];
        String seconds = args[1];
        String events = args[2];

        if (!validateRecordingName(name)) {
            cw.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        if (!seconds.matches("\\d+")) {
            cw.println(String.format("%s is an invalid recording length", seconds));
            return false;
        }

        return validateEvents(events);
    }
}
