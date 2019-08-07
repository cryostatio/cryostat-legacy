package com.redhat.rhjmc.containerjfr.platform.internal;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class PlatformStrategyModule {

    @Binds
    @IntoSet
    abstract PlatformDetectionStrategy<?> bindDefaultStrategy(DefaultPlatformStrategy s);

    @Provides
    @Singleton
    static DefaultPlatformStrategy provideDefaultStrategy(Logger logger, NetworkResolver resolver) {
        return new DefaultPlatformStrategy(logger, resolver);
    }

    @Binds
    @IntoSet
    abstract PlatformDetectionStrategy<?> bindKubeEnvStrategy(KubeEnvPlatformStrategy s);

    @Provides
    @Singleton
    static KubeEnvPlatformStrategy provideKubeEnvPlatformStrategy(Logger logger, Environment env) {
        return new KubeEnvPlatformStrategy(logger, env);
    }

    @Binds
    @IntoSet
    abstract PlatformDetectionStrategy<?> bindKubeApiStrategy(KubeApiPlatformStrategy s);

    @Provides
    @Singleton
    static KubeApiPlatformStrategy provideKubeApiPlatformStrategy(Logger logger, NetworkResolver resolver) {
        return new KubeApiPlatformStrategy(logger, resolver);
    }

}
