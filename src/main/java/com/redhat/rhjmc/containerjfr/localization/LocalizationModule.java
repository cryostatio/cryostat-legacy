package com.redhat.rhjmc.containerjfr.localization;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class LocalizationModule {
    @Provides
    @Singleton
    static LocalizationManager getLocalizationManager(Logger logger) {
        return new LocalizationManager(logger);
    }
}
