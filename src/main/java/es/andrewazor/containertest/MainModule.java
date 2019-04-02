package es.andrewazor.containertest;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.CommandExecutor.ExecutionMode;
import es.andrewazor.containertest.CommandExecutor.Mode;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.CommandsModule;

@Module(includes = { CommandsModule.class })
abstract class MainModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindRecordingExporter(RecordingExporter exporter);

    @Binds
    @IntoSet
    abstract ConnectionListener bindCommandExecutor(CommandExecutor commandExecutor);

    @Provides
    @Singleton
    static CommandExecutor provideCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry, @ExecutionMode Mode mode) {
        switch (mode) {
            case BATCH:
                return new Shell(cr, cw, commandRegistry);
            case INTERACTIVE:
                return new Shell(cr, cw, commandRegistry);
            default:
                throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static NetworkResolver provideNetworkResolver() {
        return new NetworkResolver();
    }

    @Provides
    @Singleton
    static JMCConnectionToolkit provideJMCConnectionToolkit() {
        return new JMCConnectionToolkit();
    }
}