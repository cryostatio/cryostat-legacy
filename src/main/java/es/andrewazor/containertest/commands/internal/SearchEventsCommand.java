package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.containertest.JMCConnection;

class SearchEventsCommand extends AbstractCommand {
    SearchEventsCommand(JMCConnection connection) {
        super(connection);
    }

    @Override
    public String getName() {
        return "search-events";
    }

    @Override
    public boolean validate(String[] args) {
        // TODO better validation of search term string
        return args.length == 1;
    }

    @Override
    public void execute(String[] args) throws Exception {
        service.getAvailableEventTypes()
            .stream()
            .filter(event -> event.getEventTypeID().getFullKey().toLowerCase().contains(args[0].toLowerCase()))
            .forEach(this::printEvent);
    }

    private void printEvent(IEventTypeInfo event) {
        System.out.println(String.format("\t%s", event));
    }
}