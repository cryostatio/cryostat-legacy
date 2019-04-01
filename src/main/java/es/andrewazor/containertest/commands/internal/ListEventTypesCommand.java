package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.containertest.ClientWriter;

@Singleton
class ListEventTypesCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject ListEventTypesCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "list-event-types";
    }

    /**
     * No args expected. Prints a list of available event types in the target JVM.
     */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Available event types");
        getService().getAvailableEventTypes().forEach(this::printEvent);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    private void printEvent(IEventTypeInfo event) {
        cw.println(String.format("\t%s", event));
    }
}
