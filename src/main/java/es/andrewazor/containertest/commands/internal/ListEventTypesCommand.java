package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.containertest.JMCConnection;

class ListEventTypesCommand extends AbstractCommand {
    ListEventTypesCommand(JMCConnection connection) {
        super(connection);
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
        System.out.println("Available event types");
        service.getAvailableEventTypes().forEach(this::printEvent);
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    private void printEvent(IEventTypeInfo event) {
        System.out.println(String.format("\t%s", event));
    }
}
