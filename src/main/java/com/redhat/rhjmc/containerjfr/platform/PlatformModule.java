package com.redhat.rhjmc.containerjfr.platform;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class PlatformModule {
    @Provides
    @Singleton
    static Platform providePlatform() {
        return new Platform();
    }
}
