package es.andrewazor.containertest.commands;

import java.util.Set;

import es.andrewazor.containertest.JMCConnection;

public interface CommandRegistry {
    Set<String> getRegisteredCommandNames();
    void execute(String commandName, String[] args) throws Exception;
    boolean validate(String commandName, String[] args) throws Exception;
    void setConnection(JMCConnection connection) throws Exception;
    void stop();
}
