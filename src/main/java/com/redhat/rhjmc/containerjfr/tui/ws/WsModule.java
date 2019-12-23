package com.redhat.rhjmc.containerjfr.tui.ws;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public class WsModule {
    @Provides
    @Singleton
    @ConnectionMode(ExecutionMode.WEBSOCKET)
    static CommandExecutor provideCommandExecutor(
            Logger logger,
            MessagingServer server,
            ClientReader cr,
            Lazy<SerializableCommandRegistry> commandRegistry,
            Gson gson) {
        return new WsCommandExecutor(logger, server, cr, commandRegistry, gson);
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
    static MessagingServer provideWebSocketMessagingServer(
            HttpServer server, AuthManager authManager, Logger logger, Gson gson) {
        try {
            MessagingServer messagingServer =
                    new MessagingServer(server, authManager, logger, gson);
            messagingServer.start();
            return messagingServer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
