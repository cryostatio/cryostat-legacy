package com.redhat.rhjmc.containerjfr.platform;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class PlatformModule {
    @Provides
    @Singleton
    static Platform providePlatform(Logger logger) {
        return new Platform(logger);
    }
}
