package es.andrewazor.containertest.commands.internal;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class StopRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;

    @Inject StopRecordingCommand(ClientWriter cw) {
        this.cw = cw;
    }

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

        Optional<IRecordingDescriptor> descriptor = getDescriptorByName(name);
        if (descriptor.isPresent()) {
            getService().stop(descriptor.get());
        } else {
            cw.println(String.format("Recording with name \"%s\" not found", name));
            return;
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];

            Optional<IRecordingDescriptor> descriptor = getDescriptorByName(name);
            if (descriptor.isPresent()) {
                getService().stop(descriptor.get());
                return new SuccessOutput();
            } else {
                return new FailureOutput(String.format("Recording with name \"%s\" not found", name));
            }
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument: recording name");
            return false;
        }

        String name = args[0];

        if (!validateRecordingName(name)) {
            cw.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        return true;
    }
}
