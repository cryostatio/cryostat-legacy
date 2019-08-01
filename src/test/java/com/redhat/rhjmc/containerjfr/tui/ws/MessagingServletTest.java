package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

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
    @Mock Logger logger;
    @Mock MessagingServer server;
    @Mock WebSocketServletFactory factory;
    @Mock WebSocketPolicy policy;
    @Mock Gson gson;

    @BeforeEach
    void setup() {
        servlet = new MessagingServlet(server, logger, gson);
    }

    @Test
    void testConfigure() {
        when(factory.getPolicy()).thenReturn(policy);
        servlet.configure(factory);
        verify(policy).setIdleTimeout(60_000);

        ArgumentCaptor<WebSocketCreator> creatorCaptor = ArgumentCaptor.forClass(WebSocketCreator.class);
        verify(factory).setCreator(creatorCaptor.capture());
        WebSocketCreator creator = creatorCaptor.getValue();
        Object ws = creator.createWebSocket(null, null);
        verify(server).addConnection((WsClientReaderWriter) ws);
    }

}
