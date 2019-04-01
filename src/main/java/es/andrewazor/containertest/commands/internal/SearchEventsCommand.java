package es.andrewazor.containertest.commands.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.containertest.ClientWriter;

@Singleton
class SearchEventsCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject SearchEventsCommand(ClientWriter cw) {
        this.cw = cw;
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
        Collection<? extends IEventTypeInfo> matchingEvents = getService().getAvailableEventTypes()
            .stream()
            .filter(event -> eventMatchesSearchTerm(event, args[0].toLowerCase()))
            .collect(Collectors.toList());

        if (matchingEvents.isEmpty()) {
            cw.println("\tNo matches");
        }
        matchingEvents.forEach(this::printEvent);
    }

    private void printEvent(IEventTypeInfo event) {
        cw.println(String.format("\t%s\toptions: %s", event.getEventTypeID().getFullKey(), event.getOptionDescriptors().keySet().toString()));
    }

    private boolean eventMatchesSearchTerm(IEventTypeInfo event, String term) {
        Set<String> terms = new HashSet<>();
        terms.add(event.getEventTypeID().getFullKey());
        terms.addAll(Arrays.asList(event.getHierarchicalCategory()));
        terms.add(event.getDescription());
        terms.add(event.getName());

        return terms
            .stream()
            .filter(s -> s != null)
            .map(String::toLowerCase)
            .anyMatch(s -> s.contains(term));
    }
}