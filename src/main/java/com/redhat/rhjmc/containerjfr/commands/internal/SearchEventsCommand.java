package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableEventTypeInfo;

@Singleton
class SearchEventsCommand extends AbstractConnectedCommand implements SerializableCommand {

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
        if (args.length != 1) {
            cw.println("Expected one argument: search term string");
            return false;
        }
        return true;
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

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            Collection<? extends IEventTypeInfo> matchingEvents = getService().getAvailableEventTypes()
                .stream()
                .filter(event -> eventMatchesSearchTerm(event, args[0].toLowerCase()))
                .collect(Collectors.toList());
            List<SerializableEventTypeInfo> events = new ArrayList<>(matchingEvents.size());
            for (IEventTypeInfo info : matchingEvents) {
                events.add(new SerializableEventTypeInfo(info));
            }
            return new ListOutput<>(events);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
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