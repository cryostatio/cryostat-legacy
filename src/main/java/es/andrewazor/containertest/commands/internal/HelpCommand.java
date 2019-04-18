package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import dagger.Lazy;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.tui.ClientWriter;

class HelpCommand implements Command {

    private final ClientWriter cw;
    private final Lazy<CommandRegistry> registry;

    @Inject HelpCommand(ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        this.cw = cw;
        this.registry = commandRegistry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Available commands:");
        registry.get().getAvailableCommandNames().forEach(this::printCommand);
    }

    private void printCommand(String cmd) {
        cw.println(String.format("\t%s", cmd));
    }
}

