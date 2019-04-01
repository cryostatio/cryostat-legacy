package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.Module;
import es.andrewazor.containertest.ClientWriter;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.CommandRegistry;

@Module
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

    /**
     * No args expected.
     */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Available commands:");
        registry.get().getAvailableCommandNames()
            .forEach(name -> cw.println(String.format("\t%s", name)));
    }

    @Override
    public boolean validate(String[] args) {
        return args.length == 0;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}

