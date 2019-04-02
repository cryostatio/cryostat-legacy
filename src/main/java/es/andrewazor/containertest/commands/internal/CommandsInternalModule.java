package es.andrewazor.containertest.commands.internal;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import es.andrewazor.containertest.commands.Command;
import es.andrewazor.containertest.tui.ClientWriter;

@Module
public abstract class CommandsInternalModule {
    @Binds @IntoSet abstract Command bindConnectCommand(ConnectCommand command);
    @Binds @IntoSet abstract Command bindDeleteCommand(DeleteCommand command);
    @Binds @IntoSet abstract Command bindDisconnectCommand(DisconnectCommand command);
    @Binds @IntoSet abstract Command bindDumpCommand(DumpCommand command);
    @Binds @IntoSet abstract Command bindExitCommand(ExitCommand command);
    @Binds @IntoSet abstract Command bindHelpCommand(HelpCommand command);
    @Binds @IntoSet abstract Command bindHostnameCommand(HostnameCommand command);
    @Binds @IntoSet abstract Command bindIpCommand(IpCommand command);
    @Binds @IntoSet abstract Command bindListCommand(ListCommand command);
    @Binds @IntoSet abstract Command bindListEventTypesCommand(ListEventTypesCommand command);
    @Binds @IntoSet abstract Command bindListRecordingOptionsCommand(ListRecordingOptionsCommand command);
    @Binds @IntoSet abstract Command bindSearchEventsCommand(SearchEventsCommand command);
    @Binds @IntoSet abstract Command bindSnapshotCommand(SnapshotCommand command);
    @Binds @IntoSet abstract Command bindStartRecordingCommand(StartRecordingCommand command);
    @Binds @IntoSet abstract Command bindStopRecordingCommand(StopRecordingCommand command);
    @Binds @IntoSet abstract Command bindWaitCommand(WaitCommand command);
    @Binds @IntoSet abstract Command bindWaitForCommand(WaitForCommand command);
    @Binds @IntoSet abstract Command bindWaitForDownloadCommand(WaitForDownloadCommand command);
    @Provides public static EventOptionsBuilder.Factory provideEventOptionsBuilderFactory(ClientWriter cw) {
        return new EventOptionsBuilder.Factory(cw);
    }
    @Provides public static RecordingOptionsBuilderFactory provideRecordingOptionsBuilderFactory() {
        return service -> new RecordingOptionsBuilder(service);
    }
}