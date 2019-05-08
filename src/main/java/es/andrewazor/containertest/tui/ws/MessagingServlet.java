package es.andrewazor.containertest.tui.ws;

import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

class MessagingServlet extends WebSocketServlet {

    private static final long serialVersionUID = 1L;
    private transient final MessagingServer server;
    private transient final Gson gson;

    MessagingServlet(MessagingServer server, Gson gson) {
        this.server = server;
        this.gson = gson;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(TimeUnit.MINUTES.toMillis(1));
        factory.setCreator((a, b) -> new WsClientReaderWriter(server, gson));
    }
}