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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class DumpCommandTest
        implements ValidatesTargetId, ValidatesRecordingName, ValidatesEventSpecifier {

    DumpCommand command;
    @Mock ClientWriter cw;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @Override
    public Command commandForValidationTesting() {
        return command;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(TARGET_ID, RECORDING_NAME, RECORDING_DURATION, RECORDING_EVENT_SPECIFIER);
    }

    @BeforeEach
    void setup() {
        command =
                new DumpCommand(
                        cw,
                        targetConnectionManager,
                        eventOptionsBuilderFactory,
                        recordingOptionsBuilderFactory);
    }

    @Test
    void shouldBeNamedDump() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("dump"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5})
    void shouldNotValidateIncorrectArgc(int argc) {
        Exception e =
                assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[argc]));
        String errorMessage =
                "Expected four arguments: target (host:port, ip:port, or JMX service URL), recording name, recording length, and event types";
        verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdAndRecordingName() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            ":",
                                            ":",
                                            MOCK_RECORDING_DURATION,
                                            MOCK_RECORDING_EVENT_SPECIFIER
                                        }));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording name";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording name");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidRecordingNameAndRecordingLength() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            MOCK_TARGET_ID, ":", ":", MOCK_RECORDING_EVENT_SPECIFIER
                                        }));
        String errorMessage = ": is an invalid recording name; : is an invalid recording length";
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid recording length");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidRecordingLengthAndEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            MOCK_TARGET_ID, MOCK_RECORDING_NAME, ":", ":"
                                        }));
        String errorMessage = ": is an invalid recording length; : is an invalid events specifier";
        verify(cw).println(": is an invalid recording length");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdAndRecordingLength() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            ":",
                                            MOCK_RECORDING_NAME,
                                            ":",
                                            MOCK_RECORDING_EVENT_SPECIFIER
                                        }));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording length";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording length");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidRecordingNameAndEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            MOCK_TARGET_ID, ":", MOCK_RECORDING_DURATION, ":"
                                        }));
        String errorMessage = ": is an invalid recording name; : is an invalid events specifier";
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdAndEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            ":", MOCK_RECORDING_NAME, MOCK_RECORDING_DURATION, ":"
                                        }));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid events specifier";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdRecordingNameRecordingLength() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            ":", ":", ":", MOCK_RECORDING_EVENT_SPECIFIER
                                        }));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording name; : is an invalid recording length";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid recording length");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdRecordingNameEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {":", ":", MOCK_RECORDING_DURATION, ":"}));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording name; : is an invalid events specifier";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdRecordingLengthEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () -> command.validate(new String[] {":", MOCK_RECORDING_NAME, ":", ":"}));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording length; : is an invalid events specifier";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording length");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidRecordingNameRecordingLengthEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () -> command.validate(new String[] {MOCK_TARGET_ID, ":", ":", ":"}));
        String errorMessage =
                ": is an invalid recording name; : is an invalid recording length; : is an invalid events specifier";
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid recording length");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidTargetIdRecordingNameRecordingLengthEventSpecifier() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () -> command.validate(new String[] {":", ":", ":", ":"}));
        String errorMessage =
                ": is an invalid connection specifier; : is an invalid recording name; : is an invalid recording length; : is an invalid events specifier";
        verify(cw).println(": is an invalid connection specifier");
        verify(cw).println(": is an invalid recording name");
        verify(cw).println(": is an invalid recording length");
        verify(cw).println(": is an invalid events specifier");
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldNotValidateInvalidRecordingLength() {
        Exception e =
                assertThrows(
                        FailedValidationException.class,
                        () ->
                                command.validate(
                                        new String[] {
                                            "fooHost:9091",
                                            "recording",
                                            "nine",
                                            "foo.Bar:enabled=true"
                                        }));
        String errorMessage = "nine is an invalid recording length";
        verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldValidateCorrectArgs() {
        assertDoesNotThrow(
                () ->
                        command.validate(
                                new String[] {
                                    "fooHost:9091", "recording", "30", "foo.Bar:enabled=true"
                                }));
    }

    @Test
    void shouldDumpRecordingOnExecute() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        when(recordingOptionsBuilder.name(Mockito.any())).thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.duration(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(builder.build()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        command.execute(new String[] {"fooHost:9091", "foo", "30", "foo.Bar:enabled=true"});

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        verify(recordingOptionsBuilder).name(nameCaptor.capture());
        verify(recordingOptionsBuilder).duration(durationCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());

        String actualName = nameCaptor.getValue();
        Long actualDuration = durationCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(actualDuration, Matchers.equalTo(30_000L));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder)
                .addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(), Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));
    }

    @Test
    void shouldDumpRecordingOnSerializableExecute() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        when(recordingOptionsBuilder.name(Mockito.any())).thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.duration(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(builder.build()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        SerializableCommand.Output out =
                command.serializableExecute(
                        new String[] {"fooHost:9091", "foo", "30", "foo.Bar:enabled=true"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);
        verify(recordingOptionsBuilder).name(nameCaptor.capture());
        verify(recordingOptionsBuilder).duration(durationCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());

        String actualName = nameCaptor.getValue();
        Long actualDuration = durationCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(actualDuration, Matchers.equalTo(30_000L));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder)
                .addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(), Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));
    }

    @Test
    void shouldHandleNameCollisionOnExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        command.execute(new String[] {"fooHost:9091", "foo", "30", "foo.Bar:enabled=true"});

        verify(service).getAvailableRecordings();
        verify(cw).println("Recording with name \"foo\" already exists");
    }

    @Test
    void shouldHandleNameCollisionOnSerializableExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        SerializableCommand.Output<?> out =
                command.serializableExecute(
                        new String[] {"fooHost:9091", "foo", "30", "foo.Bar:enabled=true"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.FailureOutput) out).getPayload(),
                Matchers.equalTo("Recording with name \"foo\" already exists"));

        verify(service).getAvailableRecordings();
    }

    @Test
    void shouldHandleExceptionOnSerializableExecute() throws Exception {
        when(targetConnectionManager.executeConnectedTask(
                        Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        SerializableCommand.Output<?> out =
                command.serializableExecute(
                        new String[] {"fooHost:9091", "foo", "30", "foo.Bar:enabled=true"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.ExceptionOutput) out).getPayload(),
                Matchers.equalTo("NullPointerException: "));
    }
}
