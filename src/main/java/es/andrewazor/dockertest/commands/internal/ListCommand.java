package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.dockertest.commands.Command;

class ListCommand implements Command {
    @Override
    public String getName() {
        return "list";
    }

    @Override
    public void execute(IFlightRecorderService service, String[] args) throws Exception {
        System.out.println("Available recordings:");
        for (IRecordingDescriptor recording : service.getAvailableRecordings()) {
            System.out.println(String.format("\t%s", recording));
        }
    }
}
