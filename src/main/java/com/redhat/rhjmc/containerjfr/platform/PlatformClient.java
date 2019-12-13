package com.redhat.rhjmc.containerjfr.platform;

import java.util.List;

import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.NoopAuthManager;

public interface PlatformClient {
    List<ServiceRef> listDiscoverableServices();

    default AuthManager getAuthManager() {
        return new NoopAuthManager();
    }
}
