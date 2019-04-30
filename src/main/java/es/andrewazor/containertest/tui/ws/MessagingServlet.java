package es.andrewazor.containertest.tui.ws;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

@SuppressWarnings("serial")
class MessagingServlet extends WebSocketServlet {

    private final MessagingServer server;

    MessagingServlet(MessagingServer server) {
        this.server = server;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(10_000);
        factory.setCreator((a, b) -> new WsClientReaderWriter(server));
    }
}