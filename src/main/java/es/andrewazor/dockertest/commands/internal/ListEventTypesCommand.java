package es.andrewazor.dockertest.commands.internal;

import java.util.Collection;
import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import es.andrewazor.dockertest.JMCConnection;

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
        Collection<? extends IEventTypeInfo> events = service.getAvailableEventTypes();
        for (IEventTypeInfo event : events) {
            System.out.println(String.format("\t%s", event));
        }
    }
}
