package es.andrewazor.containertest.tui.ws;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "This class will be (de)serialized by Gson, so not all fields may be accessed directly"
)
abstract class ResponseMessage extends WsMessage {
    int status;
    String message;
}