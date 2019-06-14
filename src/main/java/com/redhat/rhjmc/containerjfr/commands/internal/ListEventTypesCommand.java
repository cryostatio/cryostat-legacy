package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SerializableEventTypeInfo;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

@Singleton
class ListEventTypesCommand extends AbstractConnectedCommand implements SerializableCommand {

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
        cw.println("Available event types:");
        getService().getAvailableEventTypes().forEach(this::printEvent);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            Collection<? extends IEventTypeInfo> origInfos = getService().getAvailableEventTypes();
            List<SerializableEventTypeInfo> infos = new ArrayList<>(origInfos.size());
            for (IEventTypeInfo info : origInfos) {
                infos.add(new SerializableEventTypeInfo(info));
            }
            return new ListOutput<>(infos);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    private void printEvent(IEventTypeInfo event) {
        cw.println(String.format("\t%s", event));
    }
}
