package com.redhat.rhjmc.containerjfr.platform;

import java.util.List;

public interface PlatformClient {
    List<ServiceRef> listDiscoverableServices();
}
