package es.andrewazor.dockertest.commands.internal;

import java.lang.reflect.Method;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.commands.Command;

class ListCommand extends AbstractCommand {

    ListCommand(IFlightRecorderService service) {
        super(service);
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available recordings:");
        for (IRecordingDescriptor recording : service.getAvailableRecordings()) {
            System.out.println(toString(recording));
        }
    }

    private static String toString(IRecordingDescriptor descriptor) throws Exception {
        StringBuilder sb = new StringBuilder();

        for (Method m : descriptor.getClass().getDeclaredMethods()) {
            if (m.getParameterTypes().length == 0 && (m.getName().startsWith("get") || m.getName().startsWith("is"))) {
                sb.append("\t" + m.getName());
                sb.append("\t\t" + m.invoke(descriptor));
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
