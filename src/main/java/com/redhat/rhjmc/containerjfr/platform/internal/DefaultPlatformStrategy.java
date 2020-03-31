package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.NoopAuthManager;

class DefaultPlatformStrategy implements PlatformDetectionStrategy<DefaultPlatformClient> {

    private final Logger logger;
    private final NoopAuthManager authMgr;
    private final JvmDiscoveryClient discoveryClient;

    DefaultPlatformStrategy(
            Logger logger, NoopAuthManager authMgr, JvmDiscoveryClient discoveryClient) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public DefaultPlatformClient getPlatformClient() {
        logger.info("Selected Default Platform Strategy");
        try {
            discoveryClient.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new DefaultPlatformClient(logger, discoveryClient);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr;
    }
}
