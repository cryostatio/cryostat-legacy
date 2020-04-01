package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;

class KubeEnvPlatformStrategy implements PlatformDetectionStrategy<KubeEnvPlatformClient> {

    private final Logger logger;
    private final AuthManager authMgr;
    private final Environment env;

    KubeEnvPlatformStrategy(Logger logger, AuthManager authMgr, Environment env) {
        this.logger = logger;
        this.authMgr = authMgr;
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
    public KubeEnvPlatformClient getPlatformClient() {
        logger.info("Selected KubeEnv Platform Strategy");
        return new KubeEnvPlatformClient(env);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr;
    }
}
