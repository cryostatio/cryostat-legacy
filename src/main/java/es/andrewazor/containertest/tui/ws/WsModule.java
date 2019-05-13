package es.andrewazor.containertest.tui.ws;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import es.andrewazor.containertest.ExecutionMode;
import es.andrewazor.containertest.commands.SerializableCommandRegistry;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.tui.CommandExecutor;
import es.andrewazor.containertest.tui.ConnectionMode;

@Module
public class WsModule {
    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static CommandExecutor provideCommandExecutor(MessagingServer server, ClientReader cr, ClientWriter cw,
            Lazy<SerializableCommandRegistry> commandRegistry, Gson gson) {
        return new WsCommandExecutor(server, cr, cw, commandRegistry, gson);
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
    static MessagingServer provideWebSocketMessagingServer(@Named("LISTEN_PORT") int port, Gson gson) {
        try {
            MessagingServer messagingServer = new MessagingServer(port, gson);
            messagingServer.start();
            return messagingServer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}