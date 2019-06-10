package es.andrewazor.containertest.commands.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import es.andrewazor.containertest.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final RecordingExporter exporter;

    @Inject
    ListCommand(ClientWriter cw, RecordingExporter exporter) {
        this.cw = cw;
        this.exporter = exporter;
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
    public Output<?> serializableExecute(String[] args) {
        try {
            List<IRecordingDescriptor> origDescriptors = getService().getAvailableRecordings();
            List<HyperlinkedSerializableRecordingDescriptor> descriptors = new ArrayList<>(origDescriptors.size());
            for (IRecordingDescriptor desc : origDescriptors) {
                descriptors.add(new HyperlinkedSerializableRecordingDescriptor(desc,
                        exporter.getDownloadURL(desc.getName()), exporter.getReportURL(desc.getName())));
            }
            return new ListOutput<>(descriptors);
        } catch (Exception e) {
            return new ExceptionOutput(e);
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
                sb.append("\t") ;
                sb.append(m.getName());

                sb.append("\t\t");
                sb.append(m.invoke(descriptor));

                sb.append("\n");
            }
        }

        return sb.toString();
    }

}
