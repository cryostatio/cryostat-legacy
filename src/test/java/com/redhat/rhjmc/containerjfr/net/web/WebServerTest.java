package com.redhat.rhjmc.containerjfr.net.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Random;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportGenerator;

import com.google.gson.Gson;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebServerTest {

    WebServer exporter;
    @Mock HttpServer httpServer;
    @Mock NetworkConfiguration netConf;
    @Mock Environment env;
    @Mock Path recordingsPath;
    @Mock AuthManager authManager;
    Gson gson = new Gson();
    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock ReportGenerator reportGenerator;

    @BeforeEach
    void setup() {
        exporter =
                new WebServer(
                        httpServer,
                        netConf,
                        env,
                        recordingsPath,
                        authManager,
                        gson,
                        reportGenerator,
                        logger);
    }

    @Test
    void shouldDoNothingOnInit() {
        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(httpServer);
    }

    @Test
    void shouldSuccessfullyInstantiateWithDefaultServer() {
        assertDoesNotThrow(
                () ->
                        new WebServer(
                                httpServer,
                                netConf,
                                env,
                                recordingsPath,
                                authManager,
                                gson,
                                reportGenerator,
                                logger));
    }

    @Test
    void shouldRefreshRecordingsOnConnectionChange() throws Exception {
        when(connection.getService()).thenReturn(service);

        exporter.connectionChanged(connection);

        verify(connection).getService();
        verify(service).getAvailableRecordings();

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldThrowExceptionIfServerCannotStart() {
        Throwable cause = new SocketException();
        Exception e =
                assertThrows(
                        SocketException.class,
                        () -> {
                            doThrow(cause).when(httpServer).start();

                            exporter.start();
                        });
        MatcherAssert.assertThat(e, Matchers.equalTo(cause));
    }

    @Test
    void shouldStartEvenWhileDisconnectedFromTarget() throws Exception {
        exporter.start();

        verify(httpServer).start();
    }

    @Test
    void shouldDoNothingIfStartedWhileRunning() throws Exception {
        when(httpServer.isAlive()).thenReturn(true);

        exporter.start();

        verifyNoMoreInteractions(httpServer);
        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldDoNothingIfStartedWhileDisconnected() throws Exception {
        when(httpServer.isAlive()).thenReturn(true);

        exporter.connectionChanged(null);

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
        when(httpServer.isSsl()).thenReturn(false);

        MatcherAssert.assertThat(
                exporter.getHostUrl(), Matchers.equalTo(new URL("http", "foo", defaultPort, "")));
    }

    @Test
    void shouldUseConfiguredHostWithSSL() throws Exception {
        int defaultPort = 1234;
        when(netConf.getExternalWebServerPort()).thenReturn(defaultPort);
        when(netConf.getWebServerHost()).thenReturn("foo");
        when(httpServer.isSsl()).thenReturn(true);

        MatcherAssert.assertThat(
                exporter.getHostUrl(), Matchers.equalTo(new URL("https", "foo", defaultPort, "")));
    }

    @Test
    void shouldUseConfiguredPort() throws Exception {
        int defaultPort = 1234;
        when(netConf.getExternalWebServerPort()).thenReturn(defaultPort);
        when(netConf.getWebServerHost()).thenReturn("foo");

        MatcherAssert.assertThat(
                exporter.getHostUrl(), Matchers.equalTo(new URL("http", "foo", 1234, "")));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideDownloadUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        MatcherAssert.assertThat(
                exporter.getDownloadURL(recordingName),
                Matchers.equalTo("http://example.com:8181/recordings/" + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideDownloadUrlWithHttps(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        when(httpServer.isSsl()).thenReturn(true);

        MatcherAssert.assertThat(
                exporter.getDownloadURL(recordingName),
                Matchers.equalTo("https://example.com:8181/recordings/" + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideReportUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        MatcherAssert.assertThat(
                exporter.getReportURL(recordingName),
                Matchers.equalTo("http://example.com:8181/reports/" + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideReportUrlWithHttps(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        when(httpServer.isSsl()).thenReturn(true);

        MatcherAssert.assertThat(
                exporter.getReportURL(recordingName),
                Matchers.equalTo("https://example.com:8181/reports/" + recordingName));
    }

    @Test
    void shouldHandleClientUrlRequest() throws SocketException, UnknownHostException {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(netConf.getWebServerHost()).thenReturn("hostname");
        when(netConf.getExternalWebServerPort()).thenReturn(1);

        exporter.handleClientUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"clientUrl\":\"ws://hostname:1/command\"}");
    }

    @Test
    void shouldHandleClientUrlRequestWithWss() throws SocketException, UnknownHostException {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(netConf.getWebServerHost()).thenReturn("hostname");
        when(netConf.getExternalWebServerPort()).thenReturn(1);
        when(httpServer.isSsl()).thenReturn(true);

        exporter.handleClientUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"clientUrl\":\"wss://hostname:1/command\"}");
    }

    @Test
    void shouldHandleGrafanaDatasourceUrlRequest() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        String url = "http://hostname:1/path?query=value";
        when(env.getEnv("GRAFANA_DATASOURCE_URL", "")).thenReturn(url);

        exporter.handleGrafanaDatasourceUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"grafanaDatasourceUrl\":\"" + url + "\"}");
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequest() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        String url = "http://hostname:1/path?query=value";
        when(env.getEnv("GRAFANA_DASHBOARD_URL", "")).thenReturn(url);

        exporter.handleGrafanaDashboardUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"grafanaDashboardUrl\":\"" + url + "\"}");
    }

    @Test
    void shouldHandleRecordingDownloadRequest() throws FlightRecorderException {
        when(connection.getService()).thenReturn(service);
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.endHandler(any())).thenReturn(rep);

        byte[] src = new byte[1024 * 1024];
        new Random(123456).nextBytes(src);
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        String recordingName = "foo";
        when(descriptor.getName()).thenReturn(recordingName);
        when(service.openStream(descriptor, false)).thenReturn(new ByteArrayInputStream(src));

        Buffer dst = Buffer.buffer(1024 * 1024);
        when(rep.write(any(Buffer.class)))
                .thenAnswer(
                        invocation -> {
                            Buffer chunk = invocation.getArgument(0);
                            dst.appendBuffer(chunk);
                            return null;
                        });

        exporter.connectionChanged(connection);
        exporter.addRecording(descriptor);
        exporter.handleRecordingDownloadRequest(recordingName, ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
        assertArrayEquals(src, dst.getBytes());
    }

    @Test
    void shouldHandleReportPageRequest()
            throws FlightRecorderException, IOException, CouldNotLoadRecordingException {
        when(connection.getService()).thenReturn(service);
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        InputStream ins = mock(InputStream.class);
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        String recordingName = "foo";
        String content = "foobar";
        when(descriptor.getName()).thenReturn(recordingName);
        when(service.openStream(descriptor, false)).thenReturn(ins);
        when(reportGenerator.generateReport(ins)).thenReturn(content);

        exporter.connectionChanged(connection);
        exporter.addRecording(descriptor);
        exporter.handleReportPageRequest(recordingName, ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
        verify(rep).end(content);
    }
}
