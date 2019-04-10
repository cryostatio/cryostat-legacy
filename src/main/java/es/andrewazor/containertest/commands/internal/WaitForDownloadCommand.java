package es.andrewazor.containertest.commands.internal;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.sys.Clock;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class WaitForDownloadCommand extends WaitForCommand {

    private final RecordingExporter exporter;

    @Inject WaitForDownloadCommand(ClientWriter cw, Clock clock, RecordingExporter exporter) {
        super(cw, clock);
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "wait-for-download";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording download.
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        if (!getDescriptorByName(name).isPresent()) {
            cw.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }

        cw.println(String.format("Waiting for download of recording \"%s\" at %s", name, this.exporter.getDownloadURL(name)));
        while (this.exporter.getDownloadCount(name) < 1) {
            clock.sleep(TimeUnit.SECONDS, 1);
        }
    }
}