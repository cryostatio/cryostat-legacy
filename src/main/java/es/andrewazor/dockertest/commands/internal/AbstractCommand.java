package es.andrewazor.dockertest.commands.internal;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.Command;

abstract class AbstractCommand implements Command {
    protected final IFlightRecorderService service;

    protected AbstractCommand(IFlightRecorderService service) {
        this.service = service;
    }
}
