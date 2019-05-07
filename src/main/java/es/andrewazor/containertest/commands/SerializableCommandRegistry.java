package es.andrewazor.containertest.commands;

public interface SerializableCommandRegistry extends BaseCommandRegistry {
    SerializableCommand.Output execute(String commandName, String[] args);
}