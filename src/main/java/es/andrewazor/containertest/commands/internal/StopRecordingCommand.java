package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@Singleton
class StopRecordingCommand extends AbstractConnectedCommand {

    @Inject StopRecordingCommand() { }

    @Override
    public String getName() {
        return "stop";
    }

    /**
     * Argument is recording name
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];

        IRecordingDescriptor descriptor = null;
        for (IRecordingDescriptor d : getService().getAvailableRecordings()) {
            if (name.equals(d.getName())) {
                descriptor = d;
                break;
            }
        }

        if (descriptor == null) {
            System.out.println(String.format("Recording with name \"%s\" not found", name));
            return;
        }

        getService().stop(descriptor);
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            System.out.println("Expected one argument: recording name.");
            return false;
        }

        String name = args[0];

        if (!name.matches("[\\w-_]+")) {
            System.out.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        return true;
    }
}
