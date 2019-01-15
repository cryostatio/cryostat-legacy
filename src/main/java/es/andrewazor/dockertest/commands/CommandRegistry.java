package es.andrewazor.dockertest.commands;

public interface CommandRegistry {
    void execute(String commandName, String[] args) throws Exception;
}
