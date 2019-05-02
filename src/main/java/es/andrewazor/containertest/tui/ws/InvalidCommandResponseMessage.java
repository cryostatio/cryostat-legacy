package es.andrewazor.containertest.tui.ws;

class InvalidCommandResponseMessage extends ResponseMessage {
    InvalidCommandResponseMessage() {
        this.status = -1;
    }
}