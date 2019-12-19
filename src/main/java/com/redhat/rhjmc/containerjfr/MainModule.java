package com.redhat.rhjmc.containerjfr;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.CommandsModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;
import com.redhat.rhjmc.containerjfr.platform.PlatformModule;
import com.redhat.rhjmc.containerjfr.sys.SystemModule;
import com.redhat.rhjmc.containerjfr.tui.TuiModule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Module;
import dagger.Provides;

@Module(
        includes = {
            PlatformModule.class,
            WebModule.class,
            SystemModule.class,
            CommandsModule.class,
            TuiModule.class
        })
public abstract class MainModule {
    @Provides
    @Singleton
    static Logger provideLogger() {
        return Logger.INSTANCE;
    }

    // public since this is useful to use directly in tests
    @Provides
    @Singleton
    public static Gson provideGson() {
        return new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    }

    @Provides
    @Named("RECORDINGS_PATH")
    static Path provideSavedRecordingsPath() {
        return Paths.get("flightrecordings");
    }
}
