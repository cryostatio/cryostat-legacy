package com.redhat.rhjmc.containerjfr.tui.ws;

import org.apache.commons.lang3.exception.ExceptionUtils;

class CommandExceptionResponseMessage extends ResponseMessage<String> {
    CommandExceptionResponseMessage(String commandName, Exception e) {
        this(commandName, ExceptionUtils.getMessage(e));
    }

    CommandExceptionResponseMessage(String commandName, String message) {
        super(commandName, -2, message);
    }
}