package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.net.JMCConnection;

public interface ConnectionListener {
    void connectionChanged(JMCConnection connection);
}