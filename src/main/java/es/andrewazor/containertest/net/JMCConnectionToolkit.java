package es.andrewazor.containertest.net;

import es.andrewazor.containertest.sys.Clock;
import es.andrewazor.containertest.tui.ClientWriter;

public class JMCConnectionToolkit {

    private final ClientWriter cw;
    private final Clock clock;

    JMCConnectionToolkit(ClientWriter cw, Clock clock) {
        this.cw = cw;
        this.clock = clock;
    }

    public JMCConnection connect(String host) throws Exception {
        return connect(host, JMCConnection.DEFAULT_PORT);
    }

    public JMCConnection connect(String host, int port) throws Exception {
        return new JMCConnection(cw, clock, host, port);
    }
}