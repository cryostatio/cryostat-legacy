package es.andrewazor.containertest.tui;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.net.ConnectionListener;
import es.andrewazor.containertest.tui.CommandExecutor.ExecutionMode;

@Module
public abstract class TuiModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindCommandExecutor(CommandExecutor commandExecutor);

    @Provides
    @Singleton
    static CommandExecutor provideCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry, ExecutionMode mode) {
        switch (mode) {
        case BATCH:
            return new BatchModeExecutor(cr, cw, commandRegistry);
        case SOCKET:
            return new SocketInteractiveShellExecutor(cr, cw, commandRegistry);
        case INTERACTIVE:
            return new InteractiveShellExecutor(cr, cw, commandRegistry);
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientReader provideClientReader(ExecutionMode mode, @Nullable SocketClientReaderWriter socketReaderWriter) {
        switch (mode) {
        case BATCH:
            return new NoOpClientReader();
        case INTERACTIVE:
            return new TtyClientReader();
        case SOCKET:
            return socketReaderWriter;
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientWriter provideClientWriter(ExecutionMode mode, @Nullable SocketClientReaderWriter socketReaderWriter) {
        switch (mode) {
        case BATCH:
        case INTERACTIVE:
            return new TtyClientWriter();
        case SOCKET:
            return socketReaderWriter;
        default:
            throw new RuntimeException(String.format("Unknown execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Nullable
    @Singleton
    static SocketClientReaderWriter provideSocketClientReaderWriter(ExecutionMode mode, @Named("LISTEN_PORT") int port) {
        if (!mode.equals(ExecutionMode.SOCKET)) {
            return null;
        }
        try {
            return new SocketClientReaderWriter(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}