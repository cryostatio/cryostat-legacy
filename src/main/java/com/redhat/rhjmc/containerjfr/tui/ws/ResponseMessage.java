package com.redhat.rhjmc.containerjfr.tui.ws;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.builder.ToStringBuilder;

@SuppressFBWarnings(
        value = "URF_UNREAD_FIELD",
        justification =
                "This class will be (de)serialized by Gson, so not all fields may be accessed directly")
abstract class ResponseMessage<T> extends WsMessage {
    String commandName;
    int status;
    T payload;

    ResponseMessage(String commandName, int status, T payload) {
        this.commandName = commandName;
        this.status = status;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append(status).append(commandName).append(payload).build();
    }
}
