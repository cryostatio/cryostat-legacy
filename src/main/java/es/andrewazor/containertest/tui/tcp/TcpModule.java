package es.andrewazor.containertest.tui.tcp;

import java.io.IOException;

import javax.inject.Named;
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

@Module
public class TcpModule {
    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.SOCKET)
    static CommandExecutor provideCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new SocketInteractiveShellExecutor(cr, cw, commandRegistry);
    }

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