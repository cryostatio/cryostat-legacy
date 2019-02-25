package es.andrewazor.containertest.commands.internal;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.Module;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.commands.CommandRegistry;

@Module
class HelpCommand implements Command {

    private final Lazy<CommandRegistry> registry;

    @Inject HelpCommand(Lazy<CommandRegistry> commandRegistry) {
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
        System.out.println("Available commands:");
        registry.get().getAvailableCommandNames()
            .forEach(name -> System.out.println(String.format("\t%s", name)));
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}

