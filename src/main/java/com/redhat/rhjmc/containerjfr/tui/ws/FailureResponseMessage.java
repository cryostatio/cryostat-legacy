package com.redhat.rhjmc.containerjfr.tui.ws;

class FailureResponseMessage extends ResponseMessage<String> {
    FailureResponseMessage(String id, String commandName, String message) {
        super(id, -1, commandName, message);
    }
}
