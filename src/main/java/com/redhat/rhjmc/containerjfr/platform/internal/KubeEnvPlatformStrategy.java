package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;

class KubeEnvPlatformStrategy implements PlatformDetectionStrategy<KubeEnvPlatformClient> {

    private final Logger logger;
    private final Environment env;

    KubeEnvPlatformStrategy(Logger logger, Environment env) {
        this.logger = logger;
        this.env = env;
    }

    @Override
    public int getPriority() {
        return PRIORITY_PLATFORM;
    }

    @Override
    public boolean isAvailable() {
        logger.trace("Testing KubeEnv Platform Availability");
        return env.getEnv().keySet().stream().anyMatch(s -> s.equals("KUBERNETES_SERVICE_HOST"));
    }

    @Override
    public KubeEnvPlatformClient get() {
        logger.trace("Selected KubeEnv Platform Strategy");
        return new KubeEnvPlatformClient(env);
    }
}
