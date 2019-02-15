package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import es.andrewazor.containertest.commands.Command;

public class ExitCommand implements Command {

    public static final String NAME = "exit";

    @Inject ExitCommand() { }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public void execute(String[] args) {
    };

}