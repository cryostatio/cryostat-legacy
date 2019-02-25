package es.andrewazor.containertest.commands;

public interface Command {
    String getName();
    void execute(String[] args) throws Exception;
    boolean validate(String[] args);
    boolean isAvailable();
}
