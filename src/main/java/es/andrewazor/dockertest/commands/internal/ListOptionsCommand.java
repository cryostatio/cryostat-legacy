package es.andrewazor.dockertest.commands.internal;

import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;

class ListOptionsCommand implements Command {
    @Override
    public String getName() {
        return "list-options";
    }

    @Override
    public void execute(IFlightRecorderService service, String[] args) throws Exception {
        System.out.println("Available recording options:");
        Map<String, IOptionDescriptor<?>> options = service.getAvailableRecordingOptions();
        for (Map.Entry<String, IOptionDescriptor<?>> entry : options.entrySet()) {
            System.out.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
        }
    }
}
