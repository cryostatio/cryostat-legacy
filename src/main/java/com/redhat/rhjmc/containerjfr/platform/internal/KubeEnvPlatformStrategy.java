package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.documentation_messages.DocumentationMessageManager;

class KubeEnvPlatformStrategy implements PlatformDetectionStrategy<KubeEnvPlatformClient> {

    private final Logger logger;
    private final Environment env;
    private final DocumentationMessageManager dmm;

    KubeEnvPlatformStrategy(Logger logger, Environment env, DocumentationMessageManager dmm) {
        this.logger = logger;
        this.env = env;
        this.dmm = dmm;
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
        return new KubeEnvPlatformClient(logger, env, dmm);
    }
}
