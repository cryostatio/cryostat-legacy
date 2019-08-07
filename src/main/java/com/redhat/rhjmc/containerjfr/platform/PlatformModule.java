package com.redhat.rhjmc.containerjfr.platform;

import java.util.Set;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.platform.internal.PlatformDetectionStrategy;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformStrategyModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
    PlatformStrategyModule.class
})
public abstract class PlatformModule {
    @Provides
    @Singleton
    static PlatformClient providePlatformClient(Set<PlatformDetectionStrategy<?>> strategies) {
        return strategies
            .stream()
            .sorted()
            .filter(PlatformDetectionStrategy::isAvailable)
            .findFirst()
            .orElseThrow()
            .get();
    }
}
