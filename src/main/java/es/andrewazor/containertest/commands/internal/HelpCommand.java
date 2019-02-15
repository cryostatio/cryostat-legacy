package es.andrewazor.containertest.commands.internal;

import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.CommandRegistryFactory;

class HelpCommand implements Command {

    static final String NAME = "help";

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Available commands:");
        CommandRegistryFactory.getInstance().getRegisteredCommandNames()
            .forEach(name -> System.out.println(String.format("\t%s", name)));
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }
}

