package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.JMCConnection;

class WaitForCommand extends AbstractCommand {
    WaitForCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "wait-for";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        IRecordingDescriptor descriptor = getByName(name);
        if (descriptor == null) {
            System.out.println(String.format("Recording with name \"%s\" not found in target JVM", name));
            return;
        }

        while (!descriptor.getState().equals(IRecordingDescriptor.RecordingState.STOPPED)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) { }
            descriptor = getByName(name);
        }
    }

    private IRecordingDescriptor getByName(String name) throws FlightRecorderException {
        for (IRecordingDescriptor descriptor : service.getAvailableRecordings()) {
            if (descriptor.getName().equals(name)) {
                return descriptor;
            }
        }
        return null;
    }
}
