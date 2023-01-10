/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.net.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Set;

import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;

import com.google.gson.Gson;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebServerTest {

    WebServer exporter;
    @Mock HttpServer httpServer;
    @Mock NetworkConfiguration netConf;
    @Mock AuthManager authManager;
    @Mock Logger logger;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock Path recordingsPath;
    @Mock io.vertx.core.Vertx vertx;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        exporter =
                new WebServer(
                        httpServer, netConf, Set.of(), gson, authManager, logger, recordingsPath);
    }

    @Test
    void shouldDoNothingOnInit() {
        verifyNoInteractions(connection);
        verifyNoInteractions(service);
        verifyNoInteractions(httpServer);
    }

    @Test
    void shouldSuccessfullyInstantiateWithDefaultServer() {
        assertDoesNotThrow(
                () ->
                        new WebServer(
                                httpServer,
                                netConf,
                                Set.of(),
                                gson,
                                authManager,
                                logger,
                                recordingsPath));
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
    void shouldProvideSavedDownloadUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException,
                    URISyntaxException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        String sourceTarget = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        MatcherAssert.assertThat(
                exporter.getArchivedDownloadURL(sourceTarget, recordingName),
                Matchers.equalTo(
                        "http://example.com:8181/api/beta/recordings/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/"
                                + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideDownloadUrl(String recordingName) throws URISyntaxException, IOException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        JMXServiceURL jmxUrl =
                new JMXServiceURL(
                        "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi");
        when(connection.getJMXURL()).thenReturn(jmxUrl);

        MatcherAssert.assertThat(
                exporter.getDownloadURL(connection, recordingName),
                Matchers.equalTo(
                        "http://example.com:8181/api/v1/targets/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/recordings/"
                                + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideDownloadUrlWithHttps(String recordingName)
            throws URISyntaxException, IOException {
        when(httpServer.isSsl()).thenReturn(true);
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        JMXServiceURL jmxUrl =
                new JMXServiceURL(
                        "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi");
        when(connection.getJMXURL()).thenReturn(jmxUrl);

        MatcherAssert.assertThat(
                exporter.getDownloadURL(connection, recordingName),
                Matchers.equalTo(
                        "https://example.com:8181/api/v1/targets/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/recordings/"
                                + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideReportUrl(String recordingName) throws URISyntaxException, IOException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        JMXServiceURL jmxUrl =
                new JMXServiceURL(
                        "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi");
        when(connection.getJMXURL()).thenReturn(jmxUrl);

        MatcherAssert.assertThat(
                exporter.getReportURL(connection, recordingName),
                Matchers.equalTo(
                        "http://example.com:8181/api/v1/targets/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/reports/"
                                + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideSavedReportUrl(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException,
                    URISyntaxException {
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);

        String sourceTarget = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        MatcherAssert.assertThat(
                exporter.getArchivedReportURL(sourceTarget, recordingName),
                Matchers.equalTo(
                        "http://example.com:8181/api/beta/reports/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/"
                                + recordingName));
    }

    @ParameterizedTest()
    @ValueSource(
            strings = {"foo", "bar.jfr", "some-recording.jfr", "another_recording", "alpha123"})
    void shouldProvideReportUrlWithHttps(String recordingName)
            throws URISyntaxException, IOException {
        when(httpServer.isSsl()).thenReturn(true);
        when(netConf.getWebServerHost()).thenReturn("example.com");
        when(netConf.getExternalWebServerPort()).thenReturn(8181);
        JMXServiceURL jmxUrl =
                new JMXServiceURL(
                        "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi");
        when(connection.getJMXURL()).thenReturn(jmxUrl);

        MatcherAssert.assertThat(
                exporter.getReportURL(connection, recordingName),
                Matchers.equalTo(
                        "https://example.com:8181/api/v1/targets/service:jmx:rmi:%2F%2Flocalhost:9091%2Fjndi%2Frmi:%2F%2FfooHost:9091%2Fjmxrmi/reports/"
                                + recordingName));
    }
}
