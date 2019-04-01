package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IConstrainedMap;

import es.andrewazor.containertest.ClientWriter;
import es.andrewazor.containertest.RecordingExporter;

@Singleton
class DumpCommand extends AbstractRecordingCommand {

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

        if (getService().getAvailableRecordings().stream().anyMatch(recording -> recording.getName().equals(name))) {
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
    public boolean validate(String[] args) {
        if (args.length != 3) {
            cw.println("Expected three arguments: recording name, recording length, and event types.");
            return false;
        }

        String name = args[0];
        String seconds = args[1];
        String events = args[2];

        if (!name.matches("[\\w-_]+")) {
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
