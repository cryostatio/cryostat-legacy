package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.Set;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.openshift.OpenShiftPlatformStrategy;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public abstract class PlatformStrategyModule {

    @Provides
    @ElementsIntoSet
    static Set<PlatformDetectionStrategy<?>> providePlatformDetectionStrategies(
            Logger logger,
            NetworkResolver resolver,
            Environment env,
            FileSystem fs,
            JvmDiscoveryClient discoveryClient) {
        return Set.of(
                new OpenShiftPlatformStrategy(logger, env, resolver, fs),
                new KubeApiPlatformStrategy(logger, resolver),
                new KubeEnvPlatformStrategy(logger, env),
                new DefaultPlatformStrategy(logger, discoveryClient));
    }
}
