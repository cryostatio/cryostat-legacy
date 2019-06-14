package com.redhat.rhjmc.containerjfr.net;

public interface ConnectionListener {
    void connectionChanged(JMCConnection connection);
}