package com.redhat.rhjmc.containerjfr.platform;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class PlatformModule {
    @Provides
    @Singleton
    static Platform providePlatform(Logger logger, Environment env, NetworkResolver resolver) {
        return new Platform(logger, env, resolver);
    }
}
