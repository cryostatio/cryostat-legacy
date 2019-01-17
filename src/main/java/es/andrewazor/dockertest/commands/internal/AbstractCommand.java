package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.IConnectionHandle;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;

abstract class AbstractCommand implements Command {
    protected final IFlightRecorderService service;
    protected final IConnectionHandle handle;

    protected AbstractCommand(IFlightRecorderService service, IConnectionHandle handle) {
        this.service = service;
        this.handle = handle;
    }
}
