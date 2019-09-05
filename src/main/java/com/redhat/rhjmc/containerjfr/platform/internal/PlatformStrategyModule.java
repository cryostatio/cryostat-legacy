package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.Set;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public abstract class PlatformStrategyModule {

    @Provides
    @ElementsIntoSet
    static Set<PlatformDetectionStrategy<?>> providePlatformDetectionStrategies(Logger logger, NetworkResolver resolver, Environment env) {
        return Set.of(
                new KubeApiPlatformStrategy(logger, resolver),
                new KubeEnvPlatformStrategy(logger, env),
                new DefaultPlatformStrategy(logger, resolver)
                );
    }

}
