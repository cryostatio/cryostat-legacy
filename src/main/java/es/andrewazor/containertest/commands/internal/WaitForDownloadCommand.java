package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class WaitForDownloadCommand extends WaitForCommand {

    private final RecordingExporter exporter;

    @Inject WaitForDownloadCommand(ClientWriter cw, RecordingExporter exporter) {
        super(cw);
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
        IRecordingDescriptor descriptor = getByName(name);
        if (descriptor == null) {
            cw.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }

        cw.println(String.format("Waiting for download of recording \"%s\" at %s", name, this.exporter.getDownloadURL(name)));
        while (this.exporter.getDownloadCount(name) < 1) {
            Thread.sleep(1000);
        }
    }
}