package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.documentation_messages.DocumentationMessageManager;

class DefaultPlatformStrategy implements PlatformDetectionStrategy<DefaultPlatformClient> {

    private final Logger logger;
    private final JvmDiscoveryClient discoveryClient;
    private final DocumentationMessageManager dmm;

    DefaultPlatformStrategy(
            Logger logger, JvmDiscoveryClient discoveryClient, DocumentationMessageManager dmm) {
        this.logger = logger;
        this.discoveryClient = discoveryClient;
        this.dmm = dmm;
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
        return new DefaultPlatformClient(logger, discoveryClient, dmm);
    }
}
