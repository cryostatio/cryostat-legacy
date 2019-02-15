package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.containertest.JMCConnection;

class ListEventTypesCommand extends AbstractCommand {

    static final String NAME = "list-event-types";

    ListEventTypesCommand(JMCConnection connection) {
        super(connection);
    }

    /**
     * No args expected. Prints a list of available event types in the target JVM.
     */
    @Override
    public void execute(String[] args) throws Exception {
        validateConnection();
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
