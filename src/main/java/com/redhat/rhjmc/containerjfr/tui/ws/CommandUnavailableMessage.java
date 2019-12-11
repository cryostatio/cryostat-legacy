package com.redhat.rhjmc.containerjfr.tui.ws;

class CommandUnavailableMessage extends InvalidCommandResponseMessage {
    CommandUnavailableMessage(String commandName) {
        super(commandName);
        this.payload = String.format("Command %s unavailable", commandName);
    }
}
