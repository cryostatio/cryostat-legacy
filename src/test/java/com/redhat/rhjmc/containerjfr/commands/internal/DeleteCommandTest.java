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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

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

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

@ExtendWith(MockitoExtension.class)
class DeleteCommandTest {

    DeleteCommand command;
    @Mock ClientWriter cw;
    @Mock IRecordingDescriptor recordingDescriptor;
    @Mock JFRConnection connection;
    @Mock WebServer exporter;

    @BeforeEach
    void setup() throws FlightRecorderException {
        command = new DeleteCommand(cw, exporter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDelete() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("delete"));
    }

    @Test
    void shouldCloseNamedRecording() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        command.execute(new String[] {"foo-recording"});
        verify(connection.getService()).close(recordingDescriptor);
        verify(exporter).removeRecording(recordingDescriptor);
    }

    @Test
    void shouldReturnSerializedSuccess() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"foo-recording"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        verify(connection.getService()).close(recordingDescriptor);
        verify(exporter).removeRecording(recordingDescriptor);
    }

    @Test
    void shouldNotCloseUnnamedRecording() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        command.execute(new String[] {"bar-recording"});
        verify(connection.getService(), never()).close(recordingDescriptor);
        verifyZeroInteractions(exporter);
        verify(cw).println("No recording with name \"bar-recording\" found");
    }

    @Test
    void shouldReturnSerializedFailure() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"bar-recording"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                (((SerializableCommand.FailureOutput) out).getPayload()),
                Matchers.equalTo("No recording with name \"bar-recording\" found"));

        verify(connection.getService(), never()).close(recordingDescriptor);
        verifyZeroInteractions(exporter);
    }

    @Test
    void shouldReturnSerializedException() throws Exception {
        IFlightRecorderService service = mock(IFlightRecorderService.class);
        when(service.getAvailableRecordings())
                .thenReturn(Collections.singletonList(recordingDescriptor));
        when(connection.getService()).thenReturn(service);
        when(recordingDescriptor.getName()).thenReturn("foo-recording");
        doThrow(FlightRecorderException.class).when(service).close(Mockito.any());

        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"foo-recording"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(
                (((SerializableCommand.ExceptionOutput) out).getPayload()),
                Matchers.equalTo("FlightRecorderException: "));
    }

    @Test
    void shouldValidateCorrectArgc() {
        assertTrue(command.validate(new String[1]));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldInvalidateIncorrectArgc(int c) {
        assertFalse(command.validate(new String[c]));
        verify(cw).println("Expected one argument: recording name");
    }
}
