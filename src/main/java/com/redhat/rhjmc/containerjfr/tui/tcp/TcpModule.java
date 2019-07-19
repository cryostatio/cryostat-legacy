package com.redhat.rhjmc.containerjfr.tui.tcp;

import java.io.IOException;

import javax.inject.Named;
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
public abstract class TcpModule {
    @Provides
    @Singleton
    static SocketInteractiveShellExecutor provideSocketInteractiveShellExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new SocketInteractiveShellExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @ConnectionMode(ExecutionMode.SOCKET)
    static CommandExecutor provideCommandExecutor(SocketInteractiveShellExecutor executor) {
        return executor;
    }

    @Binds
    @IntoSet
    abstract ConnectionListener bindConnectionListener(SocketInteractiveShellExecutor commandExecutor);

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.SOCKET)
    static ClientReader provideClientReader(SocketClientReaderWriter socketReaderWriter) {
        return socketReaderWriter;
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.SOCKET)
    static ClientWriter provideClientWriter(SocketClientReaderWriter socketReaderWriter) {
        return socketReaderWriter;
    }

    @Provides
    @Singleton
    static SocketClientReaderWriter provideSocketClientReaderWriter(@Named("LISTEN_PORT") int port) {
        try {
            return new SocketClientReaderWriter(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}