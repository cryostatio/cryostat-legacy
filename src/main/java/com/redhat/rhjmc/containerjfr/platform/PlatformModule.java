package com.redhat.rhjmc.containerjfr.platform;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class PlatformModule {
    @Provides
    @Singleton
    static Platform providePlatform(Environment env) {
        return new Platform(env);
    }
}
