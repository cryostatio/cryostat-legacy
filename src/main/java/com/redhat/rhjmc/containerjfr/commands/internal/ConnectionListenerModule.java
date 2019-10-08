package com.redhat.rhjmc.containerjfr.commands.internal;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;

@Module
public abstract class ConnectionListenerModule {
    @Binds @IntoSet abstract ConnectionListener bindDeleteCommand(DeleteCommand command);
    @Binds @IntoSet abstract ConnectionListener bindDisconnectCommand(DisconnectCommand command);
    @Binds @IntoSet abstract ConnectionListener bindDumpCommand(DumpCommand command);
    @Binds @IntoSet abstract ConnectionListener bindIsConnectedCommand(IsConnectedCommand command);
    @Binds @IntoSet abstract ConnectionListener bindListCommand(ListCommand command);
    @Binds @IntoSet abstract ConnectionListener bindListEventTypesCommand(ListEventTypesCommand command);
    @Binds @IntoSet abstract ConnectionListener bindListRecordingOptionsCommand(ListRecordingOptionsCommand command);
    @Binds @IntoSet abstract ConnectionListener bindRecordingOptionsCustomizerCommand(RecordingOptionsCustomizerCommand command);
    @Binds @IntoSet abstract ConnectionListener bindSaveRecordingCommand(SaveRecordingCommand command);
    @Binds @IntoSet abstract ConnectionListener bindSearchEventsCommand(SearchEventsCommand command);
    @Binds @IntoSet abstract ConnectionListener bindSnapshotCommand(SnapshotCommand command);
    @Binds @IntoSet abstract ConnectionListener bindStartRecordingCommand(StartRecordingCommand command);
    @Binds @IntoSet abstract ConnectionListener bindStopRecordingCommand(StopRecordingCommand command);
    @Binds @IntoSet abstract ConnectionListener bindUploadRecordingCommand(UploadRecordingCommand command);
    @Binds @IntoSet abstract ConnectionListener bindWaitForCommand(WaitForCommand command);
    @Binds @IntoSet abstract ConnectionListener bindWaitForDownloadCommand(WaitForDownloadCommand command);
}
