package com.redhat.rhjmc.containerjfr.platform;

import java.util.List;

import com.redhat.rhjmc.containerjfr.net.AuthManager;

public interface PlatformClient {
    List<ServiceRef> listDiscoverableServices();

    AuthManager getAuthManager();
}
