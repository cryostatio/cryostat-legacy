package es.andrewazor.containertest;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.commands.CommandsModule;

@Module(includes = {
    CommandsModule.class,
    Shell.class
})
abstract class MainModule {
    @Binds @IntoSet abstract ConnectionListener bindRecordingExporter(RecordingExporter exporter);
    @Binds @IntoSet abstract ConnectionListener bindShell(Shell shell);
    @Provides @Singleton static NetworkResolver provideNetworkResolver() {
        return new NetworkResolver();
    }
    @Provides @Singleton static JMCConnectionToolkit provideJMCConnectionToolkit() {
        return new JMCConnectionToolkit();
    }
}