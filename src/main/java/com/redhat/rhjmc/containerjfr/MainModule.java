package com.redhat.rhjmc.containerjfr;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.redhat.rhjmc.containerjfr.commands.CommandsModule;
import com.redhat.rhjmc.containerjfr.net.NetworkModule;
import com.redhat.rhjmc.containerjfr.sys.SystemModule;
import com.redhat.rhjmc.containerjfr.tui.TuiModule;
import dagger.Module;
import dagger.Provides;

@Module(includes = {
    NetworkModule.class,
    SystemModule.class,
    CommandsModule.class,
    TuiModule.class
})
abstract class MainModule {
    @Provides
    @Singleton
    static Gson provideGson() {
        return new GsonBuilder()
            .serializeNulls()
            .create();
    }

    @Provides
    @Named("RECORDINGS_PATH")
    static Path provideSavedRecordingsPath() {
        return Paths.get("flightrecordings");
    }
}