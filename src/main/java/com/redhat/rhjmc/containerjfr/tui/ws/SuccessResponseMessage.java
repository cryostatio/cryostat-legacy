package com.redhat.rhjmc.containerjfr.tui.ws;

class SuccessResponseMessage<T> extends ResponseMessage<T> {
    SuccessResponseMessage(String id, String commandName, T t) {
        super(id, 0, commandName, t);
    }
}
