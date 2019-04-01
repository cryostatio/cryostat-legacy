package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.RecordingExporter;
import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class SnapshotCommandTest extends TestBase {

    private SnapshotCommand command;
    @Mock private JMCConnection connection;
    @Mock private IFlightRecorderService service;
    @Mock private RecordingExporter exporter;
    @Mock private EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock private RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command = new SnapshotCommand(mockClientWriter, exporter, eventOptionsBuilderFactory,
                recordingOptionsBuilderFactory);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedSnapshot() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("snapshot"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
        assertFalse(command.validate(new String[1]));
    }

    @Test
    void shouldRenameAndExportSnapshot() throws Exception {
        IRecordingDescriptor snapshot = mock(IRecordingDescriptor.class);
        when(connection.getService()).thenReturn(service);
        when(service.getSnapshotRecording()).thenReturn(snapshot);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(recordingOptionsBuilder);
        IConstrainedMap<String> builtMap = mock(IConstrainedMap.class);
        when(recordingOptionsBuilder.build()).thenReturn(builtMap);

        when(snapshot.getName()).thenReturn("Snapshot");
        when(snapshot.getId()).thenReturn(1L);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());

        command.execute(new String[0]);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Latest snapshot: \"snapshot-1\"\n"));
        verify(service).getSnapshotRecording();
        verify(service).updateRecordingOptions(Mockito.same(snapshot), Mockito.same(builtMap));

        ArgumentCaptor<IRecordingDescriptor> captor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(exporter).addRecording(captor.capture());
        IRecordingDescriptor renamed = captor.getValue();
        MatcherAssert.assertThat(renamed.getName(), Matchers.equalTo("snapshot-1"));
    }

}