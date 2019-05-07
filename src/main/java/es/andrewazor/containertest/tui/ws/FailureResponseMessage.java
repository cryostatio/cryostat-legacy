package es.andrewazor.containertest.tui.ws;

class FailureResponseMessage extends ResponseMessage<String> {
    FailureResponseMessage(String commandName, String message) {
        super(commandName, -1, message);
    }
}