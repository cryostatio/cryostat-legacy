package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;

public interface ConnectionListener {
    void connectionChanged(JFRConnection connection);
}
