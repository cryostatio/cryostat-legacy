package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.sys.Clock;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

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