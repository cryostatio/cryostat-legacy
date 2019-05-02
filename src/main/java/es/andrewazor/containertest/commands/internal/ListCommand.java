package es.andrewazor.containertest.commands.internal;

import java.lang.reflect.Method;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject ListCommand(ClientWriter cw) {
        this.cw = cw;
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
        cw.println("Available recordings:");
        Collection<IRecordingDescriptor> recordings = getService().getAvailableRecordings();
        if (recordings.isEmpty()) {
            cw.println("\tNone");
        }
        for (IRecordingDescriptor recording : recordings) {
            cw.println(toString(recording));
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
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
