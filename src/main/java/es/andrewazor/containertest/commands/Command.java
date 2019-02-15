package es.andrewazor.containertest.commands;

public interface Command {
    void execute(String[] args) throws Exception;
    boolean validate(String[] args);
}
