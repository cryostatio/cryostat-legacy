package com.redhat.rhjmc.containerjfr.net.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportGenerator;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@ExtendWith(MockitoExtension.class)
class WebServerTest {

    WebServer exporter;
    @Mock HttpServer httpServer;
    @Mock NetworkConfiguration netConf;
    @Mock Environment env;
    @Mock Path recordingsPath;
    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock ReportGenerator reportGenerator;

    @BeforeEach
    void setup() {
        exporter = new WebServer(httpServer, netConf, env, recordingsPath, reportGenerator, logger);
    }

    @Test
    void shouldDoNothingOnInit() {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(httpServer);
    }

    @Test
    void shouldSuccessfullyInstantiateWithDefaultServer() {
        assertDoesNotThrow(() -> new WebServer(httpServer, netConf, env, recordingsPath, reportGenerator, logger));
    }

    @Test
    void shouldRestartOnConnectionChange() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(httpServer.isAlive()).thenReturn(false);

        exporter.connectionChanged(connection);

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        InOrder inOrder = inOrder(httpServer);
        inOrder.verify(httpServer).requestHandler(null);
        inOrder.verify(httpServer).start();
        inOrder.verify(httpServer).getVertx();
        inOrder.verify(httpServer).requestHandler(notNull());

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldStopOnDisconnect() throws Exception {
        exporter.connectionChanged(null);

        InOrder inOrder = inOrder(httpServer);
        inOrder.verify(httpServer).requestHandler(null);

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldThrowExceptionIfServerCannotStart() {
        Throwable cause = new SocketException();
        Exception e = assertThrows(RuntimeException.class, () -> {
            when(connection.getService()).thenReturn(service);
            doThrow(cause).when(httpServer).start();

            exporter.connectionChanged(connection);
        });
        MatcherAssert.assertThat(e.getCause(), Matchers.equalTo(cause));
    }

    @Test
    void shouldStartEvenWhileDisconnectedFromTarget() throws Exception {
        exporter.start();

        verify(httpServer).start();
    }

    @Test
    void shouldDoNothingIfStartedWhileRunning() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(httpServer.isAlive()).thenReturn(false).thenReturn(true);

        exporter.connectionChanged(connection);

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        InOrder inOrder = inOrder(httpServer);
        inOrder.verify(httpServer).requestHandler(null);
        inOrder.verify(httpServer).start();
        inOrder.verify(httpServer).getVertx();
        inOrder.verify(httpServer).requestHandler(notNull());

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        
        exporter.start();

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldDoNothingIfStartedWhileDisconnected() throws Exception {
        when(httpServer.isAlive()).thenReturn(true);

        exporter.connectionChanged(null);
        
        verify(httpServer).requestHandler(null);

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);

        exporter.start();

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldInitializeDownloadCountsToZero() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");

        exporter.addRecording(descriptor);

        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.equalTo(0));

        verifyZeroInteractions(httpServer);
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
    }

    @Test
    void shouldReportNegativeDownloadsForUnknownRecordings() throws Exception {
        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.lessThan(0));

        verifyZeroInteractions(httpServer);
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
    }

    @Test
    void shouldAllowRemovingRecordings() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");

        exporter.addRecording(descriptor);
        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.equalTo(0));

        exporter.removeRecording(descriptor);
        MatcherAssert.assertThat(exporter.getDownloadCount("foo"), Matchers.lessThan(0));

        verifyZeroInteractions(httpServer);
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
    }

    @Test
    void shouldUseConfiguredHost() throws Exception {
        int defaultPort = 1234;
        when(netConf.getExternalWebServerPort()).thenReturn(defaultPort);
        when(netConf.getWebServerHost()).thenReturn("foo");

        MatcherAssert.assertThat(exporter.getHostUrl(), Matchers.equalTo(new URL("http", "foo", defaultPort, "")));
    }

    @Test
    void shouldUseConfiguredPort() throws Exception {
        int defaultPort = 1234;
        when(netConf.getExternalWebServerPort()).thenReturn(defaultPort);
        when(netConf.getWebServerHost()).thenReturn("foo");

        MatcherAssert.assertThat(exporter.getHostUrl(), Matchers.equalTo(new URL("http", "foo", 1234, "")));
    }

    @ParameterizedTest()
    @ValueSource(strings = {
        "foo",
        "bar.jfr",
        "some-recording.jfr",
        "another_recording",
        "alpha123"
    })
    void shouldProvideDownloadUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        MatcherAssert.assertThat(exporter.getDownloadURL(recordingName), Matchers.equalTo("http://example.com:8181/recordings/" + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(strings = {
        "foo",
        "bar.jfr",
        "some-recording.jfr",
        "another_recording",
        "alpha123"
    })
    void shouldProvideReportUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        MatcherAssert.assertThat(exporter.getReportURL(recordingName), Matchers.equalTo("http://example.com:8181/reports/" + recordingName));
    }

}
