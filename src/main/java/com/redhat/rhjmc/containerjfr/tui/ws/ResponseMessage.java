package com.redhat.rhjmc.containerjfr.tui.ws;

import org.apache.commons.lang3.builder.ToStringBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
        value = "URF_UNREAD_FIELD",
        justification =
                "This class will be (de)serialized by Gson, so not all fields may be accessed directly")
abstract class ResponseMessage<T> extends WsMessage {
    String id;
    String commandName;
    int status;
    T payload;

    ResponseMessage(String id, int status, String commandName, T payload) {
        this.id = id;
        this.status = status;
        this.commandName = commandName;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(status)
                .append(commandName)
                .append(payload)
                .build();
    }
}
