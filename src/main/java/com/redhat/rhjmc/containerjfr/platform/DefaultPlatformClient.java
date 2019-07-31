package com.redhat.rhjmc.containerjfr.platform;

import java.util.Collections;
import java.util.List;

class DefaultPlatformClient implements PlatformClient {
    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return Collections.emptyList();
    }
}
