package es.andrewazor.containertest.commands.internal;

import java.util.Collection;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

@Singleton
class SearchEventsCommand extends AbstractConnectedCommand {

    @Inject SearchEventsCommand() { }

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
        Collection<? extends IEventTypeInfo> matchingEvents = getService().getAvailableEventTypes()
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