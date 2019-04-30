package es.andrewazor.containertest.tui.ws;

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
import es.andrewazor.containertest.tui.tcp.SocketInteractiveShellExecutor;

@Module
public class WsModule {
    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static CommandExecutor provideCommandExecutor(ClientReader cr, ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry) {
        return new SocketInteractiveShellExecutor(cr, cw, commandRegistry);
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static ClientReader provideClientReader(MessagingServer webSocketMessagingServer) {
        return webSocketMessagingServer.getClientReader();
    }

    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static ClientWriter provideClientWriter(MessagingServer webSocketMessagingServer) {
        return webSocketMessagingServer.getClientWriter();
    }

    @Provides
    @Singleton
    static MessagingServer provideWebSocketMessagingServer(@Named("LISTEN_PORT") int port) {
        try {
            MessagingServer messagingServer = new MessagingServer(port);
            messagingServer.start();
            return messagingServer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}