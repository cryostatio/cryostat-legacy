package com.redhat.rhjmc.containerjfr.tui.tty;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

@Module
public abstract class TtyModule {
    @Provides
    @Singleton
    static InteractiveShellExecutor provideInteractiveShellExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new InteractiveShellExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @ConnectionMode(ExecutionMode.INTERACTIVE)
    static CommandExecutor provideCommandExecutor(InteractiveShellExecutor executor) {
        return executor;
    }

    @Binds
    @IntoSet
    abstract ConnectionListener bindConnectionListener(InteractiveShellExecutor commandExecutor);

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