package es.andrewazor.containertest.tui.tty;

import javax.inject.Singleton;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.tui.CommandExecutor;
import es.andrewazor.containertest.tui.CommandExecutor.ExecutionMode;
import es.andrewazor.containertest.tui.ConnectionMode;
import es.andrewazor.containertest.tui.InteractiveShellExecutor;

@Module
public class TtyModule {
    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.INTERACTIVE)
    static CommandExecutor provideInteractiveCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new InteractiveShellExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.BATCH)
    static CommandExecutor provideBatchCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new BatchModeExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.INTERACTIVE)
    static ClientReader provideInteractiveClientReader() {
        return new TtyClientReader();
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.BATCH)
    static ClientReader provideBatchClientReader() {
        return new NoOpClientReader();
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.INTERACTIVE)
    static ClientWriter provideInteractiveClientWriter() {
        return new TtyClientWriter();
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.BATCH)
    static ClientWriter provideBatchClientWriter() {
        return new TtyClientWriter();
    }
}