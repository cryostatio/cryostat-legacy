package es.andrewazor.containertest.tui;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.tui.CommandExecutor.ExecutionMode;
import es.andrewazor.containertest.tui.CommandExecutor.Mode;

@Module
public abstract class TuiModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindCommandExecutor(CommandExecutor commandExecutor);

    @Provides
    @Singleton
    static CommandExecutor provideCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry, @ExecutionMode Mode mode) {
        switch (mode) {
        case BATCH:
            return new BatchModeExecutor(cr, cw, commandRegistry);
        case INTERACTIVE:
            return new InteractiveShellExecutor(cr, cw, commandRegistry);
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientReader provideClientReader(@ExecutionMode Mode mode) {
        switch (mode) {
        case BATCH:
            return new NoOpClientReader();
        case INTERACTIVE:
            return new TtyClientReader();
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientWriter provideClientWriter(@ExecutionMode Mode mode) {
        switch (mode) {
        case BATCH:
            // fall through
        case INTERACTIVE:
            return new TtyClientWriter();
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }
}