package es.andrewazor.containertest.tui.ws;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingServletTest {

    MessagingServlet servlet;
    @Mock MessagingServer server;
    @Mock WebSocketServletFactory factory;
    @Mock WebSocketPolicy policy;

    @BeforeEach
    void setup() {
        servlet = new MessagingServlet(server);
    }

    @Test
    void testConfigure() {
        when(factory.getPolicy()).thenReturn(policy);
        servlet.configure(factory);
        verify(policy).setIdleTimeout(10_000);

        ArgumentCaptor<WebSocketCreator> creatorCaptor = ArgumentCaptor.forClass(WebSocketCreator.class);
        verify(factory).setCreator(creatorCaptor.capture());
        WebSocketCreator creator = creatorCaptor.getValue();
        Object ws = creator.createWebSocket(null, null);
        verify(server).setConnection((WsClientReaderWriter) ws);
    }

}