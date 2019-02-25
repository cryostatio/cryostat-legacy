package es.andrewazor.containertest.commands.internal;

import java.lang.reflect.Method;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@Singleton
class ListCommand extends AbstractConnectedCommand {

    @Inject ListCommand() { }

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
        Collection<IRecordingDescriptor> recordings = getService().getAvailableRecordings();
        if (recordings.isEmpty()) {
            System.out.println("\tNone");
        }
        for (IRecordingDescriptor recording : recordings) {
            System.out.println(toString(recording));
        }
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
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
