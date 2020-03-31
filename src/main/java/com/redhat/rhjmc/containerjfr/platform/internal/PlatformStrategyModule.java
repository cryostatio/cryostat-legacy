package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.Set;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.net.NoopAuthManager;
import com.redhat.rhjmc.containerjfr.platform.openshift.OpenShiftAuthManager;
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
            OpenShiftAuthManager openShiftAuthManager,
            NoopAuthManager noopAuthManager,
            NetworkResolver resolver,
            Environment env,
            FileSystem fs,
            JvmDiscoveryClient discoveryClient) {
        return Set.of(
                new OpenShiftPlatformStrategy(logger, openShiftAuthManager, env, resolver, fs),
                new KubeApiPlatformStrategy(logger, noopAuthManager, resolver),
                new KubeEnvPlatformStrategy(logger, noopAuthManager, env),
                new DefaultPlatformStrategy(logger, noopAuthManager, discoveryClient));
    }
}
