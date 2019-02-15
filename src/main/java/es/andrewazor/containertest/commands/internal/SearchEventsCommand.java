package es.andrewazor.containertest.commands.internal;

import java.util.Collection;
import java.util.stream.Collectors;

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
        validateConnection();
        Collection<? extends IEventTypeInfo> matchingEvents = service.getAvailableEventTypes()
            .stream()
            .filter(event -> event.getEventTypeID().getFullKey().toLowerCase().contains(args[0].toLowerCase()))
            .collect(Collectors.toList());

        if (matchingEvents.isEmpty()) {
            System.out.println("\tNo matches");
        }
        matchingEvents.forEach(this::printEvent);
    }

    private void printEvent(IEventTypeInfo event) {
        System.out.println(String.format("\t%s\toptions: %s", event.getEventTypeID().getFullKey(), event.getOptionDescriptors().keySet().toString()));
    }
}