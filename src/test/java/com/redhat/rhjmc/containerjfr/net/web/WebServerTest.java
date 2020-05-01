/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@ExtendWith(MockitoExtension.class)
class WebServerTest {

    WebServer exporter;
    @Mock HttpServer httpServer;
    @Mock NetworkConfiguration netConf;
    @Mock Environment env;
    @Mock Path recordingsPath;
    @Mock com.redhat.rhjmc.containerjfr.core.sys.FileSystem fs;
    @Mock AuthManager authManager;
    Gson gson = MainModule.provideGson();
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
                        fs,
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
                                fs,
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
                Matchers.equalTo("http://example.com:8181/api/v1/recordings/" + recordingName));
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
                Matchers.equalTo("https://example.com:8181/api/v1/recordings/" + recordingName));
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
                Matchers.equalTo("http://example.com:8181/api/v1/reports/" + recordingName));
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
                Matchers.equalTo("https://example.com:8181/api/v1/reports/" + recordingName));
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
        verify(rep).end("{\"clientUrl\":\"ws://hostname:1/api/v1/command\"}");
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
        verify(rep).end("{\"clientUrl\":\"wss://hostname:1/api/v1/command\"}");
    }

    @Test
    void shouldHandleGrafanaDatasourceUrlRequest() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        String url = "http://hostname:1/path?query=value";
        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DATASOURCE_URL", "")).thenReturn(url);

        exporter.handleGrafanaDatasourceUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"grafanaDatasourceUrl\":\"" + url + "\"}");
    }

    @Test
    void shouldHandleGrafanaDatasourceUrlRequestWithoutEnvVar() {
        RoutingContext ctx = mock(RoutingContext.class);

        when(env.hasEnv("GRAFANA_DATASOURCE_URL")).thenReturn(false);

        HttpStatusException e =
                assertThrows(
                        HttpStatusException.class,
                        () -> exporter.handleGrafanaDatasourceUrlRequest(ctx));
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("Internal Server Error"));
        MatcherAssert.assertThat(
                e.getPayload(), Matchers.equalTo("Deployment has no Grafana " + "configuration"));
        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequest() {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        String url = "http://hostname:1/path?query=value";
        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(true);
        when(env.getEnv("GRAFANA_DASHBOARD_URL", "")).thenReturn(url);

        exporter.handleGrafanaDashboardUrlRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"grafanaDashboardUrl\":\"" + url + "\"}");
    }

    @Test
    void shouldHandleGrafanaDashboardUrlRequestWithoutEnvVar() {
        RoutingContext ctx = mock(RoutingContext.class);

        when(env.hasEnv("GRAFANA_DASHBOARD_URL")).thenReturn(false);

        HttpStatusException e =
                assertThrows(
                        HttpStatusException.class,
                        () -> exporter.handleGrafanaDashboardUrlRequest(ctx));
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("Internal Server Error"));
        MatcherAssert.assertThat(
                e.getPayload(), Matchers.equalTo("Deployment has no Grafana " + "configuration"));
        MatcherAssert.assertThat(e.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldHandleRecordingUploadRequest() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String savePath = "/some/path/";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);

        when(fs.isDirectory(recordingsPath)).thenReturn(true);

        Set<FileUpload> uploads = new HashSet<>();
        FileUpload upload = mock(FileUpload.class);
        uploads.add(upload);
        when(ctx.fileUploads()).thenReturn(uploads);
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");

        Path filePath = mock(Path.class);
        when(filePath.toString()).thenReturn(savePath + filename);
        when(recordingsPath.resolve(filename)).thenReturn(filePath);

        Vertx vertx = mock(Vertx.class);
        when(httpServer.getVertx()).thenReturn(vertx);

        FileSystem fs = mock(FileSystem.class);
        when(vertx.fileSystem()).thenReturn(fs);

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return false;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        })
                .when(vertx)
                .executeBlocking(any(Handler.class), any(Handler.class));

        when(fs.exists(eq(savePath + filename), any(Handler.class)))
                .thenAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return false;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        });

        when(fs.move(eq("foo"), eq(savePath + filename), any(Handler.class)))
                .thenAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(2);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return true;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        });

        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        exporter.handleRecordingUploadRequest(ctx);

        verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        verify(rep).end("{\"name\":\"" + filename + "\"}");
    }

    @Test
    void shouldHandleRecordingDownloadRequest() throws Exception {
        when(authManager.validateHttpHeader(any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        when(connection.getService()).thenReturn(service);
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.endHandler(any())).thenReturn(rep);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);

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
    void shouldHandleReportPageRequest() throws Exception {
        when(authManager.validateHttpHeader(any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        when(connection.getService()).thenReturn(service);
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);

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
