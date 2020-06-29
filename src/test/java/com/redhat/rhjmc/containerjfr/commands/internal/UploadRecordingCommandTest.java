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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.MapOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.commands.internal.UploadRecordingCommand.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class UploadRecordingCommandTest {

    static final String HOST_ID = "fooHost:9091";
    static final String DATASOURCE_URL = "http://localhost:8080/load";
    static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";

    UploadRecordingCommand command;
    @Mock ClientWriter cw;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock FileSystem fs;
    @Mock Environment env;
    @Mock Path path;
    @Mock CloseableHttpClient httpClient;
    @Mock JFRConnection conn;

    @BeforeEach
    void setup() {
        this.command =
                new UploadRecordingCommand(
                        cw, targetConnectionManager, fs, env, path, () -> httpClient);
    }

    @Test
    void shouldBeNamedUploadRecording() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("upload-recording"));
    }

    @Test
    void shouldBeAvailableIfEnvVarExists() {
        Mockito.when(env.hasEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(true);
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldNotBeAvailableIfNoEnvVar() {
        Mockito.when(env.hasEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(false);
        Assertions.assertFalse(command.isAvailable());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 5})
    void shouldNotValidateWrongArgc(int c) {
        Assertions.assertFalse(command.validate(new String[c]));
        Mockito.verify(cw)
                .println(
                        "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "localhost",
                "localhost:123",
                "service:jmx:rmi:///localhost/jndi/rmi://localhost:fooHost/jmxrmi"
            })
    void shouldValidateAcceptableTargetId(String targetId) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);
        Assertions.assertTrue(command.validate(new String[] {targetId, "foo"}));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(
            strings = {"localhost:", ":123", "localhost:abc", ":abc", "http:///localhost:9091"})
    void shouldNotValidateUnacceptableTargetIds(String targetId) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);
        Assertions.assertFalse(command.validate(new String[] {targetId, "foo"}));
        Mockito.verify(cw)
                .println(String.format("%s is an invalid connection specifier", targetId));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo",
                "some_recording",
                "recording2",
                "recording.jfr",
                "1",
                "hyphenated-name"
            })
    void shouldValidateAcceptableRecordingNames(String recordingName) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);
        Assertions.assertTrue(command.validate(new String[] {HOST_ID, recordingName}));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"a recording", ".", ".jfr"})
    void shouldNotValidateUnacceptableRecordingNames(String recordingName) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);
        Assertions.assertFalse(command.validate(new String[] {HOST_ID, recordingName}));
        Mockito.verify(cw).println(String.format("%s is an invalid recording name", recordingName));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"http://localhost:8080/load", "http://localhost:8080", "http://localhost"})
    void shouldValidateAcceptableDatasourceUrls(String datasourceUrl) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(datasourceUrl);
        Assertions.assertTrue(command.validate(new String[] {HOST_ID, "foo"}));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(
            strings = {
                "foo",
                "localhost:8080",
                "http:///localhost",
                "http:://localhost",
                " http://localhost",
                "http:// localhost",
                "http://local host"
            })
    void shouldNotValidateUnacceptableDatasourceUrls(String datasourceUrl) {
        Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(datasourceUrl);
        Assertions.assertFalse(command.validate(new String[] {HOST_ID, "foo"}));
    }

    @Nested
    class RecordingSelection {

        @Test
        void shouldSelectInMemoryIfAvailable() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);

            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(command.targetConnectionManager.connect(Mockito.anyString()))
                    .thenReturn(conn);
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

            UploadRecordingCommand.RecordingConnection res =
                    command.getBestRecordingForName(HOST_ID, rec.getName());

            Assertions.assertTrue(res.getStream().isPresent());
            Assertions.assertTrue(res.getConnection().isPresent());
            MatcherAssert.assertThat(res.getStream().get(), Matchers.sameInstance(stream));
            Mockito.verify(svc).openStream(rec, false);
        }

        @Test
        void shouldReadFromDiskIfNotInMemory() throws Exception {
            InputStream stream = Mockito.mock(InputStream.class);
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            Path rec = Mockito.mock(Path.class);

            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(true);
            Mockito.when(fs.newInputStream(rec)).thenReturn(stream);

            UploadRecordingCommand.RecordingConnection res =
                    command.getBestRecordingForName(HOST_ID, "foo");

            Assertions.assertTrue(res.getStream().isPresent());
            Assertions.assertFalse(res.getConnection().isPresent());
            MatcherAssert.assertThat(
                    res.getStream().get(), Matchers.instanceOf(BufferedInputStream.class));
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotFile() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            Path rec = Mockito.mock(Path.class);

            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(false);

            UploadRecordingCommand.RecordingConnection res =
                    command.getBestRecordingForName(HOST_ID, "foo");

            Assertions.assertFalse(res.getStream().isPresent());
            Assertions.assertFalse(res.getConnection().isPresent());
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotReadable() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            Path rec = Mockito.mock(Path.class);

            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(false);

            UploadRecordingCommand.RecordingConnection res =
                    command.getBestRecordingForName(HOST_ID, "foo");

            Assertions.assertFalse(res.getStream().isPresent());
            Assertions.assertFalse(res.getConnection().isPresent());
        }
    }

    @Nested
    class ExecutionTest {

        @Test
        void shouldThrowExceptionIfRecordingNotFound() throws Exception {
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);

            Assertions.assertThrows(
                    RecordingNotFoundException.class,
                    () -> command.execute(new String[] {HOST_ID, rec.getName()}));
        }

        @Test
        void shouldDoUpload() throws Exception {
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(command.targetConnectionManager.connect(Mockito.anyString()))
                    .thenReturn(conn);
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
            Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);

            CloseableHttpResponse httpResp = Mockito.mock(CloseableHttpResponse.class);
            HttpEntity entity = Mockito.mock(HttpEntity.class);
            StatusLine status = Mockito.mock(StatusLine.class);
            Mockito.when(httpClient.execute(Mockito.any())).thenReturn(httpResp);
            Mockito.when(httpResp.getEntity()).thenReturn(entity);
            Mockito.when(httpResp.getStatusLine()).thenReturn(status);
            Mockito.when(status.toString()).thenReturn("status_line");
            Mockito.when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream("entity_response".getBytes()));

            command.execute(new String[] {HOST_ID, "foo"});

            ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
            Mockito.verify(httpClient).execute(captor.capture());
            MatcherAssert.assertThat(captor.getValue(), Matchers.instanceOf(HttpPost.class));
            HttpPost post = (HttpPost) captor.getValue();
            MatcherAssert.assertThat(post.getURI().toString(), Matchers.equalTo(DATASOURCE_URL));
            MatcherAssert.assertThat(post.getEntity(), Matchers.notNullValue());
            Mockito.verify(cw).println("[status_line] entity_response");
        }
    }

    @Nested
    class SerializableExecutionTest {

        @Test
        void shouldReturnExceptionIfRecordingNotFound() throws Exception {
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);

            Output<?> out = command.serializableExecute(new String[] {HOST_ID, rec.getName()});
            MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        }

        @Test
        void shouldDoUpload() throws Exception {
            Mockito.when(
                            targetConnectionManager.executeConnectedTask(
                                    Mockito.anyString(), Mockito.any()))
                    .thenAnswer(
                            arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(conn));
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(command.targetConnectionManager.connect(Mockito.anyString()))
                    .thenReturn(conn);
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);
            Mockito.when(env.getEnv(GRAFANA_DATASOURCE_ENV)).thenReturn(DATASOURCE_URL);

            CloseableHttpResponse httpResp = Mockito.mock(CloseableHttpResponse.class);
            HttpEntity entity = Mockito.mock(HttpEntity.class);
            StatusLine status = Mockito.mock(StatusLine.class);
            Mockito.when(httpClient.execute(Mockito.any())).thenReturn(httpResp);
            Mockito.when(httpResp.getEntity()).thenReturn(entity);
            Mockito.when(httpResp.getStatusLine()).thenReturn(status);
            Mockito.when(status.toString()).thenReturn("status_line");
            Mockito.when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream("entity_response".getBytes()));

            Output<?> out = command.serializableExecute(new String[] {HOST_ID, rec.getName()});

            MatcherAssert.assertThat(out, Matchers.instanceOf(MapOutput.class));
            MatcherAssert.assertThat(
                    out.getPayload().toString(),
                    Matchers.allOf(
                            Matchers.startsWith("{"),
                            Matchers.endsWith("}"),
                            Matchers.containsString("body=entity_response"),
                            Matchers.containsString("status=status_line"),
                            Matchers.containsString(", ")));

            ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
            Mockito.verify(httpClient).execute(captor.capture());
            MatcherAssert.assertThat(captor.getValue(), Matchers.instanceOf(HttpPost.class));
            HttpPost post = (HttpPost) captor.getValue();
            MatcherAssert.assertThat(post.getURI().toString(), Matchers.equalTo(DATASOURCE_URL));
            MatcherAssert.assertThat(post.getEntity(), Matchers.notNullValue());
        }
    }
}
