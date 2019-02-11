package es.andrewazor.containertest.commands.internal;

import java.lang.reflect.Method;
import java.util.Collection;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;

class ListCommand extends AbstractCommand {

    ListCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "list";
    }

    /**
     * No args expected. Prints list of available recordings in target JVM.
     */
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available recordings:");
        Collection<IRecordingDescriptor> recordings = service.getAvailableRecordings();
        if (recordings.isEmpty()) {
            System.out.println("\tNone");
        }
        for (IRecordingDescriptor recording : recordings) {
            System.out.println(toString(recording));
        }
    }

    @Override
    public boolean validate(String[] args) {
        return true;
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
