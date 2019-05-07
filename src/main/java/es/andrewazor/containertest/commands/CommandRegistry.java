package es.andrewazor.containertest.commands;

public interface CommandRegistry extends BaseCommandRegistry {
    void execute(String commandName, String[] args) throws Exception;
}