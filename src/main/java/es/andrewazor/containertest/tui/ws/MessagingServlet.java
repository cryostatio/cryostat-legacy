package es.andrewazor.containertest.tui.ws;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

class MessagingServlet extends WebSocketServlet {

    private static final long serialVersionUID = 1L;
    private transient final MessagingServer server;

    MessagingServlet(MessagingServer server) {
        this.server = server;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(10_000);
        factory.setCreator((a, b) -> new WsClientReaderWriter(server));
    }
}