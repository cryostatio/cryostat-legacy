package com.redhat.rhjmc.containerjfr.tui.ws;

class MalformedMessageResponseMessage extends ResponseMessage<String> {
    MalformedMessageResponseMessage(String commandName) {
        super(
                null,
                -2,
                commandName,
                String.format("Message \"%s\" appears to be malformed", commandName));
    }
}
