package com.redhat.rhjmc.containerjfr.documentation_messages;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class DocumentationMessageModule {
    @Provides
    @Singleton
    static DocumentationMessageManager getLocalizationManager(Logger logger) {
        return new DocumentationMessageManager(logger);
    }
}
