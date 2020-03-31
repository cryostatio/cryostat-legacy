package com.redhat.rhjmc.containerjfr.platform.internal;

import java.net.MalformedURLException;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.NoopAuthManager;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

class DefaultPlatformClient implements PlatformClient {

    private final Logger log;
    private final JvmDiscoveryClient discoveryClient;

    DefaultPlatformClient(Logger log, JvmDiscoveryClient discoveryClient) {
        this.log = log;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return discoveryClient.getDiscoveredJvmDescriptors().stream()
                .map(
                        u -> {
                            try {
                                return new ServiceRef(
                                        u.getJmxServiceUrl().toString(), u.getMainClass(), 0);
                            } catch (MalformedURLException e) {
                                log.info(e);
                                return null;
                            }
                        })
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    @Override
    public AuthManager getAuthManager() {
        return new NoopAuthManager(log);
    }
}
