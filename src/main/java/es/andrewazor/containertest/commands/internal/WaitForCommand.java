package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@Singleton
class WaitForCommand extends AbstractConnectedCommand {

    @Inject WaitForCommand() { }

    @Override
    public String getName() {
        return "wait-for";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording completion.
     */
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

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            System.out.println("Expected one argument");
            return false;
        }

        if (!args[0].matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", args[0]));
        }

        return true;
    }

    protected IRecordingDescriptor getByName(String name) throws FlightRecorderException {
        for (IRecordingDescriptor descriptor : getService().getAvailableRecordings()) {
            if (descriptor.getName().equals(name)) {
                return descriptor;
            }
        }
        return null;
    }
}
