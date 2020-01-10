package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.localization.LocalizationManager;

class KubeEnvPlatformStrategy implements PlatformDetectionStrategy<KubeEnvPlatformClient> {

    private final Logger logger;
    private final Environment env;
    private final LocalizationManager lm;

    KubeEnvPlatformStrategy(Logger logger, Environment env, LocalizationManager lm) {
        this.logger = logger;
        this.env = env;
        this.lm = lm;
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
        logger.info("Selected KubeEnv Platform Strategy");
        return new KubeEnvPlatformClient(logger, env, lm);
    }
}
