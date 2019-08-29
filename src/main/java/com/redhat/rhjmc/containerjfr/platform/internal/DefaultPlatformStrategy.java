package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

class DefaultPlatformStrategy implements PlatformDetectionStrategy<DefaultPlatformClient> {

    private final Logger logger;
    private final NetworkResolver resolver;

    DefaultPlatformStrategy(Logger logger, NetworkResolver resolver) {
        this.logger = logger;
        this.resolver = resolver;
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
        logger.trace("Selected Default Platform Strategy");
        return new DefaultPlatformClient(logger, resolver);
    }

}
