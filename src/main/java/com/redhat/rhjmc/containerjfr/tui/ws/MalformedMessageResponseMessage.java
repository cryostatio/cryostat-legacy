package com.redhat.rhjmc.containerjfr.tui.ws;

class MalformedMessageResponseMessage extends ResponseMessage<String> {
    MalformedMessageResponseMessage(String commandName) {
        super(
                commandName,
                -2,
                String.format("Message \"%s\" appears to be malformed", commandName));
    }
}
