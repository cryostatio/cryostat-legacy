package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.localization.LocalizationManager;

class DefaultPlatformStrategy implements PlatformDetectionStrategy<DefaultPlatformClient> {

    private final Logger logger;
    private final JvmDiscoveryClient discoveryClient;
    private final LocalizationManager lm;

    DefaultPlatformStrategy(
            Logger logger, JvmDiscoveryClient discoveryClient, LocalizationManager lm) {
        this.logger = logger;
        this.discoveryClient = discoveryClient;
        this.lm = lm;
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
    public DefaultPlatformClient get() {
        logger.info("Selected Default Platform Strategy");
        try {
            discoveryClient.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new DefaultPlatformClient(logger, discoveryClient, lm);
    }
}
