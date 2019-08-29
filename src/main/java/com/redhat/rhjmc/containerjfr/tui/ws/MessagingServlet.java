package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

class MessagingServlet extends WebSocketServlet {

    private static final long serialVersionUID = 1L;
    private transient final MessagingServer server;
    private final Logger logger;
    private transient final Gson gson;

    MessagingServlet(MessagingServer server, Logger logger, Gson gson) {
        this.server = server;
        this.logger = logger;
        this.gson = gson;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(TimeUnit.MINUTES.toMillis(1));
        factory.setCreator((a, b) -> new WsClientReaderWriter(server, logger, gson));
    }
}
