package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.commands.internal.UploadRecordingCommand.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.apache.http.impl.client.CloseableHttpClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadRecordingCommandTest {

    static final String UPLOAD_URL = "http://example.com/";

    @Mock ClientWriter cw;
    @Mock FileSystem fs;
    @Mock Path path;
    @Mock Provider<CloseableHttpClient> httpProvider;
    @Mock JFRConnection conn;
    UploadRecordingCommand cmd;

    @BeforeEach
    void setup() {
        this.cmd = new UploadRecordingCommand(cw, fs, path, httpProvider);
    }

    @Test
    void shouldBeNamedUploadRecording() {
        MatcherAssert.assertThat(cmd.getName(), Matchers.equalTo("upload-recording"));
    }

    @Test
    void shouldNotBeAvailableWhenDisconnected() {
        Assertions.assertFalse(cmd.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenConnectedButNoRecordingsPath() {
        cmd.connectionChanged(conn);
        Assertions.assertTrue(cmd.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenDisConnectedWithRecordingsPath() {
        Mockito.when(fs.isDirectory(path)).thenReturn(true);
        Assertions.assertTrue(cmd.isAvailable());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void shouldNotValidateWrongArgc(int c) {
        Assertions.assertFalse(cmd.validate(new String[c]));
        Mockito.verify(cw).println("Expected two arguments: recording name and upload URL");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"foo", "foo.jfr", "recording", "some-name", "another_name", "123", "abc123"})
    void shouldValidateRecordingNames(String recordingName) {
        Assertions.assertTrue(cmd.validate(new String[] {recordingName, UPLOAD_URL}));
    }

    @Disabled("Bug #111")
    @ParameterizedTest
    @ValueSource(strings = {".", "some recording", ""})
    void shouldNotValidateInvalidRecordingNames(String recordingName) {
        Assertions.assertFalse(cmd.validate(new String[] {recordingName, UPLOAD_URL}));
        Mockito.verify(cw).println(recordingName + " is an invalid recording name");
    }

    @Nested
    class RecordingSelection {

        @Test
        void shouldSelectInMemoryIfAvailable() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(List.of(rec));
            Mockito.when(rec.getName()).thenReturn("foo");
            Mockito.when(svc.openStream(Mockito.any(), Mockito.anyBoolean())).thenReturn(stream);

            cmd.connectionChanged(conn);
            Optional<InputStream> res = cmd.getBestRecordingForName(rec.getName());

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.sameInstance(stream));
            Mockito.verify(svc).openStream(rec, true);
        }

        @Test
        void shouldReadFromDiskIfNotConnected() throws Exception {
            Path rec = Mockito.mock(Path.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(true);
            Mockito.when(fs.newInputStream(rec)).thenReturn(stream);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.sameInstance(stream));
        }

        @Test
        void shouldFallThroughToDiskIfNotInMemory() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());

            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(true);
            Mockito.when(fs.newInputStream(rec)).thenReturn(stream);

            cmd.connectionChanged(conn);
            Optional<InputStream> res = cmd.getBestRecordingForName("foo");

            Assertions.assertTrue(res.isPresent());
            MatcherAssert.assertThat(res.get(), Matchers.sameInstance(stream));
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotFile() throws Exception {
            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(false);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");
            Assertions.assertFalse(res.isPresent());
        }

        @Test
        void shouldReturnEmptyIfNotInMemoryAndNotReadable() throws Exception {
            Path rec = Mockito.mock(Path.class);
            Mockito.when(path.resolve(Mockito.anyString())).thenReturn(rec);
            Mockito.when(fs.isRegularFile(rec)).thenReturn(true);
            Mockito.when(fs.isReadable(rec)).thenReturn(false);

            Optional<InputStream> res = cmd.getBestRecordingForName("foo");
            Assertions.assertFalse(res.isPresent());
        }
    }

    @Nested
    class ExecutionTest {

        @Test
        void shouldThrowExceptionIfRecordingNotFound() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");

            cmd.connectionChanged(conn);
            Assertions.assertThrows(
                    RecordingNotFoundException.class,
                    () -> cmd.execute(new String[] {rec.getName(), UPLOAD_URL}));
        }
    }

    @Nested
    class SerializableExecutionTest {

        @Test
        void shouldReturnExceptionIfRecordingNotFound() throws Exception {
            IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
            IRecordingDescriptor rec = Mockito.mock(IRecordingDescriptor.class);
            Mockito.when(conn.getService()).thenReturn(svc);
            Mockito.when(svc.getAvailableRecordings()).thenReturn(Collections.emptyList());
            Mockito.when(rec.getName()).thenReturn("foo");

            cmd.connectionChanged(conn);
            Output<?> out = cmd.serializableExecute(new String[] {rec.getName(), UPLOAD_URL});
            MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        }
    }
}
