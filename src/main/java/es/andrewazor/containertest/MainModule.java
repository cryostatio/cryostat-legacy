package es.andrewazor.containertest;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.commands.CommandsModule;

@Module(includes = {
    CommandsModule.class,
    Shell.class
})
abstract class MainModule {
    @Binds @IntoSet abstract ConnectionListener bindRecordingExporter(RecordingExporter exporter);
}