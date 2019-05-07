package es.andrewazor.containertest.tui.ws;

class InvalidCommandResponseMessage extends ResponseMessage<String> {
    InvalidCommandResponseMessage(String commandName) {
        super(commandName, -1, String.format("Command %s is unrecognized", commandName));
    }
}