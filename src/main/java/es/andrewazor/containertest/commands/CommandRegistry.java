package es.andrewazor.containertest.commands;

import es.andrewazor.containertest.JMCConnection;

public interface CommandRegistry {
    void execute(String commandName, String[] args) throws Exception;
    boolean validate(String commandName, String[] args) throws Exception;
    void setConnection(JMCConnection connection) throws Exception;
    void stop();
}
