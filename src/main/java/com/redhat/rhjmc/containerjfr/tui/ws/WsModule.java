package com.redhat.rhjmc.containerjfr.tui.ws;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

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