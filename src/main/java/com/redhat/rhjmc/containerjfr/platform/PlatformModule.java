package com.redhat.rhjmc.containerjfr.platform;

import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.NoopAuthManager;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformDetectionStrategy;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformStrategyModule;
import com.redhat.rhjmc.containerjfr.platform.openshift.OpenShiftAuthManager;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {PlatformStrategyModule.class})
public abstract class PlatformModule {

    static final String PLATFORM_STRATEGY_ENV_VAR = "CONTAINER_JFR_PLATFORM";
    static final String AUTH_MANAGER_ENV_VAR = "CONTAINER_JFR_AUTH_MANAGER";

    @Provides
    @Singleton
    static PlatformClient providePlatformClient(
            PlatformDetectionStrategy<?> platformStrategy, Environment env, Logger logger) {
        return new SelfDiscoveryPlatformClient(platformStrategy.getPlatformClient());
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static AuthManager providePlatformAuthManager(PlatformDetectionStrategy<?> platformStrategy) {
        return platformStrategy.getAuthManager();
    }

    @Provides
    @Singleton
    static AuthManager provideAuthManager(
            ExecutionMode mode,
            Environment env,
            FileSystem fs,
            Set<AuthManager> authManagers,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<AuthManager> platformAuthManager,
            Logger logger) {
        final String authManagerClass;
        if (env.hasEnv(AUTH_MANAGER_ENV_VAR)) {
            authManagerClass = env.getEnv(AUTH_MANAGER_ENV_VAR);
            logger.info(String.format("Selecting configured AuthManager \"%s\"", authManagerClass));
        } else if (ExecutionMode.WEBSOCKET.equals(mode)) {
            authManagerClass = platformAuthManager.get().getClass().getCanonicalName();
            logger.info(
                    String.format(
                            "Selecting platform default AuthManager \"%s\"", authManagerClass));
        } else {
            authManagerClass = NoopAuthManager.class.getCanonicalName();
        }
        return authManagers.stream()
                .filter(mgr -> Objects.equals(mgr.getClass().getCanonicalName(), authManagerClass))
                .findFirst()
                .orElseThrow(
                        () ->
                                new RuntimeException(
                                        String.format(
                                                "Selected AuthManager \"%s\" is not available",
                                                authManagerClass)));
    }

    @Provides
    @Singleton
    static PlatformDetectionStrategy<?> providePlatformStrategy(
            Logger logger, Set<PlatformDetectionStrategy<?>> strategies, Environment env) {
        if (env.hasEnv(PLATFORM_STRATEGY_ENV_VAR)) {
            String platform = env.getEnv(PLATFORM_STRATEGY_ENV_VAR);
            logger.info(
                    String.format(
                            "Selecting configured PlatformDetectionStrategy \"%s\"", platform));
            for (PlatformDetectionStrategy<?> strat : strategies) {
                if (Objects.equals(platform, strat.getClass().getCanonicalName())) {
                    return strat;
                }
            }
            throw new RuntimeException(
                    String.format("Selected PlatformDetectionStrategy \"%s\" not found", platform));
        }
        return strategies.stream()
                .sorted()
                .filter(PlatformDetectionStrategy::isAvailable)
                .findFirst()
                .orElseThrow();
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
