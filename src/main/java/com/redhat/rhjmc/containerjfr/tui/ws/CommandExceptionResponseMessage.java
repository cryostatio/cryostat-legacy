package com.redhat.rhjmc.containerjfr.tui.ws;

import org.apache.commons.lang3.exception.ExceptionUtils;

class CommandExceptionResponseMessage extends ResponseMessage<String> {
    CommandExceptionResponseMessage(String id, String commandName, Exception e) {
        this(id, commandName, ExceptionUtils.getMessage(e));
    }

    CommandExceptionResponseMessage(String id, String commandName, String message) {
        super(id, -2, commandName, message);
    }
}
