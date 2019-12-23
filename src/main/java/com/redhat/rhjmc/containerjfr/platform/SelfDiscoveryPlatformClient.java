package com.redhat.rhjmc.containerjfr.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.redhat.rhjmc.containerjfr.net.AuthManager;

class SelfDiscoveryPlatformClient implements PlatformClient {

    private static final ServiceRef VM_SELF_REF =
            new ServiceRef("localhost", "This ContainerJFR", 0);
    private final PlatformClient client;

    SelfDiscoveryPlatformClient(PlatformClient client) {
        this.client = client;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        List<ServiceRef> list = new ArrayList<>();
        list.add(VM_SELF_REF);
        list.addAll(this.client.listDiscoverableServices());
        return Collections.unmodifiableList(list);
    }

    @Override
    public AuthManager getAuthManager() {
        return this.client.getAuthManager();
    }
}
