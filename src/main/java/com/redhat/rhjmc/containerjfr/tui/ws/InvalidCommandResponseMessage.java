package com.redhat.rhjmc.containerjfr.tui.ws;

class InvalidCommandResponseMessage extends ResponseMessage<String> {
    InvalidCommandResponseMessage(String id, String commandName) {
        super(
                id,
                -1,
                commandName,
                String.format("[%s] command %s is unrecognized", id, commandName));
    }
}
