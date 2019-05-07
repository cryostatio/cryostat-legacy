package es.andrewazor.containertest.commands;

import java.util.Set;

public interface BaseCommandRegistry {
    Set<String> getRegisteredCommandNames();
    Set<String> getAvailableCommandNames();
    boolean validate(String commandName, String[] args);
    boolean isCommandAvailable(String commandName);
}
