package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;

class WaitForDownloadCommand extends WaitForCommand {

    static final String NAME = "wait-for-download";

    WaitForDownloadCommand(JMCConnection connection) {
        super(connection);
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording download.
     */
    @Override
    public void execute(String[] args) throws Exception {
        validateConnection();
        String name = args[0];
        IRecordingDescriptor descriptor = getByName(name);
        if (descriptor == null) {
            System.out.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }

        System.out.println(String.format("Waiting for download of recording \"%s\" at %s", name, connection.getRecordingExporter().getDownloadURL(name)));
        while (connection.getRecordingExporter().getDownloadCount(name) < 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) { }
        }
    }
}