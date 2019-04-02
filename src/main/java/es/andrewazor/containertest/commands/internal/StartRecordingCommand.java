package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IConstrainedMap;

import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class StartRecordingCommand extends AbstractRecordingCommand {

    private final RecordingExporter exporter;

    @Inject
    StartRecordingCommand(ClientWriter cw, RecordingExporter exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        super(cw, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "start";
    }

    /**
     * Two args expected.
     * First argument is recording name, second argument is recording length in seconds.
     * Second argument is comma-separated event options list, ex. jdk.SocketWrite:enabled=true,com.foo:ratio=95.2
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        String events = args[1];

        if (getService().getAvailableRecordings().stream().anyMatch(recording -> recording.getName().equals(name))) {
            cw.println(String.format("Recording with name \"%s\" already exists", name));
            return;
        }

        IConstrainedMap<String> recordingOptions = recordingOptionsBuilderFactory.create(getService())
            .name(name)
            .build();
        this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println("Expected two arguments: recording name and event types.");
            return false;
        }

        String name = args[0];
        String events = args[1];

        if (!name.matches("[\\w-_]+")) {
            cw.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        return validateEvents(events);
    }
}
