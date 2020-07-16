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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class SaveRecordingCommandTest implements ValidatesTargetId, ValidatesRecordingName {

    @Mock TargetConnectionManager targetConnectionManager;
    @Mock ClientWriter cw;
    @Mock Clock clock;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    SaveRecordingCommand command;

    @Override
    public Command commandForValidationTesting() {
        return command;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(TARGET_ID, RECORDING_NAME);
    }

    @BeforeEach
    void setup() {
        command = new SaveRecordingCommand(cw, targetConnectionManager, clock, fs, recordingsPath);
    }

    @Test
    void shouldBeNamedSave() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("save"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void shouldNotValidateIncorrectArgc(int argc) {
        Exception e =
                Assertions.assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[argc]));
        String errorMessage =
                "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name";
        verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdAndRecordingName() {
        Exception e =
                Assertions.assertThrows(
                        FailedValidationException.class,
                        () -> command.validate(new String[] {":", ":"}));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording name";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording name");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotBeAvailableWhenRecordingsPathNotDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(false);
        Assertions.assertFalse(command.isAvailable());
    }

    @Test
    void shouldBeAvailableWhenConnectedAndRecordingsPathIsDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldExecuteAndPrintMessageIfRecordingNotFound() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        verify(cw).println("Recording with name \"foo\" not found");
        verifyNoMoreInteractions(cw);
    }

    @Test
    void shouldExecuteAndSaveRecording() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(cw).println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndSaveDuplicatedRecording() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");
        when(fs.exists(savePath)).thenReturn(false).thenReturn(true).thenReturn(false);

        command.execute(new String[] {"fooHost:9091", "foo"});
        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(service, Mockito.times(2)).getAvailableRecordings();
        verify(fs, Mockito.times(2)).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.1.jfr");
        InOrder inOrder = Mockito.inOrder(cw);
        inOrder.verify(cw)
                .println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        inOrder.verify(cw)
                .println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.1.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndThrowWhenDuplicateRenamingExhausted() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");
        when(fs.exists(savePath)).thenReturn(true);

        Assertions.assertThrows(
                IOException.class,
                () -> {
                    command.execute(new String[] {"fooHost:9091", "foo"});
                });

        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        for (int i = 1; i < Byte.MAX_VALUE - 1; i++) {
            verify(recordingsPath, Mockito.atLeastOnce())
                    .resolve("some-host-svc-local_foo_20191129T112233Z." + i + ".jfr");
        }
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(fs);
    }

    @Test
    void shouldExecuteAndSaveRecordingWithExtension() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo.jfr");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        command.execute(new String[] {"fooHost:9091", "foo.jfr"});

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verify(cw).println("Recording saved as \"some-host-svc-local_foo_20191129T112233Z.jfr\"");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExecuteAndReturnSerializedFailureIfRecordingNotFound() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"fooHost:9091", "foo"});

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.FailureOutput) out).getPayload(),
                Matchers.equalTo("Recording with name \"foo\" not found"));
    }

    @Test
    void shouldExecuteAndReturnSerializedException() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenThrow(NullPointerException.class);

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"fooHost:9091", "foo"});

        verify(service).getAvailableRecordings();
        verifyNoMoreInteractions(service);
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
    }

    @Test
    void shouldExecuteAndSaveRecordingAndReturnSerializedRecordingName() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(recording.getName()).thenReturn("foo");
        when(connection.getService()).thenReturn(service);
        when(connection.getHost()).thenReturn("some-host.svc.local");
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        InputStream recordingStream = mock(InputStream.class);
        when(service.openStream(recording, false)).thenReturn(recordingStream);
        Path savePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(savePath);
        Instant now = mock(Instant.class);
        when(clock.now()).thenReturn(now);
        when(now.truncatedTo(Mockito.any(TemporalUnit.class))).thenReturn(now);
        when(now.toString()).thenReturn("2019-11-29T11:22:33Z");

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"fooHost:9091", "foo"});

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.StringOutput) out).getPayload(),
                Matchers.equalTo("some-host-svc-local_foo_20191129T112233Z.jfr"));

        verify(service).getAvailableRecordings();
        verify(fs).copy(recordingStream, savePath);
        verify(recordingsPath, Mockito.atLeastOnce())
                .resolve("some-host-svc-local_foo_20191129T112233Z.jfr");
        verifyNoMoreInteractions(service);
    }
}
