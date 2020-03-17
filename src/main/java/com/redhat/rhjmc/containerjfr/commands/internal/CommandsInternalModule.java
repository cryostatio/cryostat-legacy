package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class CommandsInternalModule {
    @Binds
    @IntoSet
    abstract Command bindConnectCommand(ConnectCommand command);

    @Binds
    @IntoSet
    abstract Command bindDeleteCommand(DeleteCommand command);

    @Binds
    @IntoSet
    abstract Command bindDeleteSavedRecordingCommand(DeleteSavedRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindDisconnectCommand(DisconnectCommand command);

    @Binds
    @IntoSet
    abstract Command bindDumpCommand(DumpCommand command);

    @Binds
    @IntoSet
    abstract Command bindExitCommand(ExitCommand command);

    @Binds
    @IntoSet
    abstract Command bindHelpCommand(HelpCommand command);

    @Binds
    @IntoSet
    abstract Command bindHostnameCommand(HostnameCommand command);

    @Binds
    @IntoSet
    abstract Command bindIpCommand(IpCommand command);

    @Binds
    @IntoSet
    abstract Command bindIsConnectedCommand(IsConnectedCommand command);

    @Binds
    @IntoSet
    abstract Command bindListCommand(ListCommand command);

    @Binds
    @IntoSet
    abstract Command bindListEventTypesCommand(ListEventTypesCommand command);

    @Binds
    @IntoSet
    abstract Command bindListRecordingOptionsCommand(ListRecordingOptionsCommand command);

    @Binds
    @IntoSet
    abstract Command bindListEventTemplatesCommand(ListEventTemplatesCommand command);

    @Binds
    @IntoSet
    abstract Command bindListSavedRecordingsCommand(ListSavedRecordingsCommand command);

    @Binds
    @IntoSet
    abstract Command bindPingCommand(PingCommand command);

    @Binds
    @IntoSet
    abstract Command bindPortScanCommand(ScanTargetsCommand command);

    @Binds
    @IntoSet
    abstract Command bindPrintUrlCommand(PrintUrlCommand command);

    @Binds
    @IntoSet
    abstract Command bindRecordingOptionsCustomizerCommand(
            RecordingOptionsCustomizerCommand command);

    @Binds
    @IntoSet
    abstract Command bindSaveRecordingCommand(SaveRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindSearchEventsCommand(SearchEventsCommand command);

    @Binds
    @IntoSet
    abstract Command bindSnapshotCommand(SnapshotCommand command);

    @Binds
    @IntoSet
    abstract Command bindStartRecordingCommand(StartRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindStopRecordingCommand(StopRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindUploadRecordingCommand(UploadRecordingCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitCommand(WaitCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitForCommand(WaitForCommand command);

    @Binds
    @IntoSet
    abstract Command bindWaitForDownloadCommand(WaitForDownloadCommand command);

    @Provides
    static EventOptionsBuilder.Factory provideEventOptionsBuilderFactory(ClientWriter cw) {
        return new EventOptionsBuilder.Factory(cw);
    }

    @Provides
    static RecordingOptionsBuilderFactory provideRecordingOptionsBuilderFactory(
            RecordingOptionsCustomizer customizer) {
        return service -> customizer.apply(new RecordingOptionsBuilder(service));
    }

    @Provides
    @Singleton
    static RecordingOptionsCustomizer provideRecordingOptionsCustomizer(ClientWriter cw) {
        return new RecordingOptionsCustomizer(cw);
    }

    @Provides
    @Nullable
    @Singleton
    static CommandRegistry provideCommandRegistry(
            ExecutionMode mode, ClientWriter cw, Set<Command> commands) {
        if (mode.equals(ExecutionMode.WEBSOCKET)) {
            return null;
        } else {
            return new CommandRegistryImpl(cw, commands);
        }
    }

    @Provides
    @Nullable
    @Singleton
    static SerializableCommandRegistry provideSerializableCommandRegistry(
            ExecutionMode mode, Set<Command> commands) {
        if (mode.equals(ExecutionMode.WEBSOCKET)) {
            return new SerializableCommandRegistryImpl(commands);
        } else {
            return null;
        }
    }
}
