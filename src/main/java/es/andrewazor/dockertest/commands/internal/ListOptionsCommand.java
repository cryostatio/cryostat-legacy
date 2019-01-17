package es.andrewazor.dockertest.commands.internal;

import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

class ListOptionsCommand extends AbstractCommand {
    ListOptionsCommand(IFlightRecorderService service, IConnectionHandle handle) {
        super(service, handle);
    }

    @Override
    public String getName() {
        return "list-options";
    }

    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available recording options:");
        Map<String, IOptionDescriptor<?>> options = service.getAvailableRecordingOptions();
        for (Map.Entry<String, IOptionDescriptor<?>> entry : options.entrySet()) {
            System.out.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
        }
    }
}
