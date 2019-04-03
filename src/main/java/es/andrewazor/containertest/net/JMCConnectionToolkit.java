package es.andrewazor.containertest.net;

import es.andrewazor.containertest.tui.ClientWriter;

public class JMCConnectionToolkit {

    private final ClientWriter cw;

    JMCConnectionToolkit(ClientWriter cw) {
        this.cw = cw;
    }

    public JMCConnection connect(String host) throws Exception {
        return connect(host, JMCConnection.DEFAULT_PORT);
    }

    public JMCConnection connect(String host, int port) throws Exception {
        return new JMCConnection(cw, host, port);
    }
}