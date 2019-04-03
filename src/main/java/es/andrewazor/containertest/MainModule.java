package es.andrewazor.containertest;

import dagger.Module;
import es.andrewazor.containertest.commands.CommandsModule;
import es.andrewazor.containertest.net.NetworkModule;
import es.andrewazor.containertest.sys.SystemModule;
import es.andrewazor.containertest.tui.TuiModule;

@Module(includes = {
    NetworkModule.class,
    SystemModule.class,
    CommandsModule.class,
    TuiModule.class
})
abstract class MainModule {
}