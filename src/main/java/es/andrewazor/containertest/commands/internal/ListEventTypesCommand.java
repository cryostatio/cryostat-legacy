package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

@Singleton
class ListEventTypesCommand extends AbstractConnectedCommand {

    @Inject ListEventTypesCommand() { }

    @Override
    public String getName() {
        return "list-event-types";
    }

    /**
     * No args expected. Prints a list of available event types in the target JVM.
     */
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available event types");
        getService().getAvailableEventTypes().forEach(this::printEvent);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    private void printEvent(IEventTypeInfo event) {
        System.out.println(String.format("\t%s", event));
    }
}
