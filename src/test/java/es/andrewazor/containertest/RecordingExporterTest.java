package es.andrewazor.containertest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;

@ExtendWith(MockitoExtension.class)
class RecordingExporterTest extends TestBase {

    RecordingExporter exporter;
    @Mock JMCConnection connection;
    @Mock IFlightRecorderService service;
    @Mock NetworkResolver resolver;
    @Mock NanoHTTPD server;

    @BeforeEach
    void setup() {
        exporter = new RecordingExporter(resolver, server);
    }

    @Test
    void shouldDoNothingOnInit() {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(server);
        verifyZeroInteractions(resolver);
    }

    @Test
    void shouldSuccessfullyInstantiateWithDefaultServer() {
        assertDoesNotThrow(() -> new RecordingExporter(resolver));
    }

    @Test
    void shouldRestartOnConnectionChange() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(server.isAlive())
            .thenReturn(true)
            .thenReturn(false);
        when(resolver.getHostAddress()).thenReturn("host-address");

        exporter.connectionChanged(connection);

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        InOrder inOrder = inOrder(server);
        inOrder.verify(server).isAlive();
        inOrder.verify(server).stop();
        inOrder.verify(server).isAlive();
        inOrder.verify(server).start();

        MatcherAssert.assertThat(stdout.toString(),
                Matchers.equalTo("Recordings available at http://host-address:8080/$RECORDING_NAME\n"));

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldStopOnDisconnect() throws Exception {
        when(server.isAlive()).thenReturn(true);

        exporter.connectionChanged(null);

        InOrder inOrder = inOrder(server);
        inOrder.verify(server).isAlive();
        inOrder.verify(server).stop();

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldThrowExceptionIfServerCannotStart() {
        Exception e = assertThrows(RuntimeException.class, () -> {
            when(connection.getService()).thenReturn(service);
            when(server.isAlive()).thenReturn(true).thenReturn(false);
            doThrow(IOException.class).when(server).start();

            exporter.connectionChanged(connection);
        });
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("java.io.IOException"));
    }

    @Test
    void shouldDoNothingIfStartedWhileRunning() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(server.isAlive())
            .thenReturn(true)
            .thenReturn(false)
            .thenReturn(true);
        when(resolver.getHostAddress()).thenReturn("host-address");

        exporter.connectionChanged(connection);

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        InOrder inOrder = inOrder(server);
        inOrder.verify(server).isAlive();
        inOrder.verify(server).stop();
        inOrder.verify(server).isAlive();
        inOrder.verify(server).start();

        MatcherAssert.assertThat(stdout.toString(),
                Matchers.equalTo("Recordings available at http://host-address:8080/$RECORDING_NAME\n"));

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);

        exporter.start();

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldDoNothingIfStartedWhileDisconnected() throws Exception {
        when(server.isAlive()).thenReturn(true);

        exporter.connectionChanged(null);

        InOrder inOrder = inOrder(server);
        inOrder.verify(server).isAlive();
        inOrder.verify(server).stop();

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);

        exporter.start();

        verifyNoMoreInteractions(server);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldInitializeDownloadCountsNegative() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");

        exporter.addRecording(descriptor);

        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.lessThan(0));

        verifyZeroInteractions(server);
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
    }

    @Test
    void shouldReportNegativeDownloadsForUnknownRecordings() throws Exception {
        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.lessThan(0));

        verifyZeroInteractions(server);
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
    }

}