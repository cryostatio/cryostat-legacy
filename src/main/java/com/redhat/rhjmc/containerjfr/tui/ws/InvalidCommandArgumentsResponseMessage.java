package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.Arrays;

class InvalidCommandArgumentsResponseMessage extends ResponseMessage<String> {
    InvalidCommandArgumentsResponseMessage(String id, String commandName, String[] args) {
        super(
                id,
                -1,
                commandName,
                String.format("%s are invalid arguments to %s", Arrays.asList(args), commandName));
    }
}
