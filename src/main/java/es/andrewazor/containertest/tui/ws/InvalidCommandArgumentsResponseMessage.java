package es.andrewazor.containertest.tui.ws;

import java.util.Arrays;

class InvalidCommandArgumentsResponseMessage extends ResponseMessage<String> {
    InvalidCommandArgumentsResponseMessage(String commandName, String[] args) {
        super(commandName, -1, String.format("%s are invalid arguments to %s", Arrays.asList(args), commandName));
    }
}