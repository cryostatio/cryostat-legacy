package com.redhat.rhjmc.containerjfr.tui;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.tui.tcp.TcpModule;
import com.redhat.rhjmc.containerjfr.tui.tty.TtyModule;
import com.redhat.rhjmc.containerjfr.tui.ws.WsModule;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module(includes = {TcpModule.class, TtyModule.class, WsModule.class})
public abstract class TuiModule {
    @Provides
    @Singleton
    static CommandExecutor provideCommandExecutor(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<CommandExecutor> batchExecutor,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<CommandExecutor> interactiveExecutor,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<CommandExecutor> webSocketExecutor,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<CommandExecutor> socketExecutor) {
        switch (mode) {
            case BATCH:
                return batchExecutor.get();
            case INTERACTIVE:
                return interactiveExecutor.get();
            case WEBSOCKET:
                return webSocketExecutor.get();
            case SOCKET:
                return socketExecutor.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientReader provideClientReader(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<ClientReader> batchReader,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<ClientReader> interactiveReader,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<ClientReader> webSocketReader,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<ClientReader> socketReader) {
        switch (mode) {
            case BATCH:
                return batchReader.get();
            case INTERACTIVE:
                return interactiveReader.get();
            case WEBSOCKET:
                return webSocketReader.get();
            case SOCKET:
                return socketReader.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }

    @Provides
    @Singleton
    static ClientWriter provideClientWriter(
            ExecutionMode mode,
            @ConnectionMode(ExecutionMode.BATCH) Lazy<ClientWriter> batchWriter,
            @ConnectionMode(ExecutionMode.INTERACTIVE) Lazy<ClientWriter> interactiveWriter,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<ClientWriter> webSocketWriter,
            @ConnectionMode(ExecutionMode.SOCKET) Lazy<ClientWriter> socketWriter) {
        switch (mode) {
            case BATCH:
                return batchWriter.get();
            case INTERACTIVE:
                return interactiveWriter.get();
            case SOCKET:
                return socketWriter.get();
            case WEBSOCKET:
                return webSocketWriter.get();
            default:
                throw new RuntimeException(
                        String.format("Unimplemented execution mode: %s", mode.toString()));
        }
    }
}
