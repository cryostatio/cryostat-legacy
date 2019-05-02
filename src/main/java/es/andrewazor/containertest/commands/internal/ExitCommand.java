package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.tui.ClientWriter;

public class ExitCommand implements Command {

    public static final String NAME = "exit";

    private final ClientWriter cw;

    @Inject ExitCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void execute(String[] args) { };

}