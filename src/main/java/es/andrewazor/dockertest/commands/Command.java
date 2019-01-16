package es.andrewazor.dockertest.commands;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

public interface Command {
    String getName();
    void execute(String[] args) throws Exception;
}
