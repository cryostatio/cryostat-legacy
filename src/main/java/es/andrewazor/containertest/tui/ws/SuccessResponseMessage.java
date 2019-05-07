package es.andrewazor.containertest.tui.ws;

class SuccessResponseMessage<T> extends ResponseMessage<T> {
    SuccessResponseMessage(String commandName, T t) {
        super(commandName, 0, t);
    }
}