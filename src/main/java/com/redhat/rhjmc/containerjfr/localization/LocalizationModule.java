package com.redhat.rhjmc.containerjfr.localization;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

import dagger.Module;
import dagger.Provides;

import java.io.IOException;
import java.util.Set;

@Module
public abstract class LocalizationModule {
    @Provides
    @Singleton
    static LocalizationManager getLocalizationManager(Logger logger, Set<MessageLoader> loaders) {
        try {
            LocalizationManager lm = new LocalizationManager(logger);
            for (MessageLoader l: loaders) {
                l.loadInto(lm);
            }
            return lm;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
