package com.redhat.rhjmc.containerjfr.platform;

import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformDetectionStrategy;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformStrategyModule;
import com.redhat.rhjmc.containerjfr.platform.openshift.OpenShiftAuthManager;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {PlatformStrategyModule.class})
public abstract class PlatformModule {

    static final String PLATFORM_STRATEGY_ENV_VAR = "CONTAINER_JFR_PLATFORM";

    @Provides
    @Singleton
    static PlatformClient providePlatformClient(
            Set<PlatformDetectionStrategy<?>> strategies, Environment env, Logger logger) {
        if (env.hasEnv(PLATFORM_STRATEGY_ENV_VAR)) {
            String platform = env.getEnv(PLATFORM_STRATEGY_ENV_VAR);
            logger.info(
                    String.format(
                            "Selecting configured PlatformDetectionStrategy \"%s\"", platform));
            for (PlatformDetectionStrategy<?> strat : strategies) {
                if (Objects.equals(platform, strat.getClass().getCanonicalName())) {
                    return new SelfDiscoveryPlatformClient(strat.get());
                }
            }
            throw new RuntimeException(
                    String.format("Selected PlatformDetectionStrategy \"%s\" not found", platform));
        }
        return new SelfDiscoveryPlatformClient(
                strategies.stream()
                        .sorted()
                        .filter(PlatformDetectionStrategy::isAvailable)
                        .findFirst()
                        .orElseThrow()
                        .get());
    }

    @Provides
    @Singleton
    static JvmDiscoveryClient provideJvmDiscoveryClient(Logger logger) {
        return new JvmDiscoveryClient(logger);
    }

    @Provides
    @Singleton
    static OpenShiftAuthManager provideOpenShiftAuthManager(Logger logger, FileSystem fs) {
        return new OpenShiftAuthManager(logger, fs);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindOpenShiftAuthManager(OpenShiftAuthManager mgr);
}
