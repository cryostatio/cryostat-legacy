package com.redhat.rhjmc.containerjfr.tui.ws;

class CommandUnavailableMessage extends InvalidCommandResponseMessage {
    CommandUnavailableMessage(String id, String commandName) {
        super(id, commandName);
        this.payload = String.format("Command %s unavailable", commandName);
    }
}
