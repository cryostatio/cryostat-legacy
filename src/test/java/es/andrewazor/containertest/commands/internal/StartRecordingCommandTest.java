package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

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

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.FailureOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommand.StringOutput;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class StartRecordingCommandTest {

    StartRecordingCommand command;
    @Mock ClientWriter cw;
    @Mock JMCConnection connection;
    @Mock IFlightRecorderService service;
    @Mock RecordingExporter exporter;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command = new StartRecordingCommand(cw, exporter, eventOptionsBuilderFactory,
                recordingOptionsBuilderFactory);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedStart() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("start"));
    }

    @ParameterizedTest
    @ValueSource(ints = {
        0,
        1,
        3,
    })
    void shouldNotValidateWithIncorrectArgc(int argc) {
        assertFalse(command.validate(new String[argc]));
        verify(cw).println("Expected two arguments: recording name and event types");
    }

    @Test
    void shouldNotValidateBadRecordingName() {
        assertFalse(command.validate(new String[]{ ".", "foo.Bar:enabled=true" }));
        verify(cw).println(". is an invalid recording name");
    }

    @Test
    void shouldNotValidateBadEventString() {
        assertFalse(command.validate(new String[]{ "foo", "foo.Bar:=true" }));
        verify(cw).println("foo.Bar:=true is an invalid events pattern");
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[]{ "foo", "foo.Bar:enabled=true" }));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldStartRecordingOnExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        when(recordingOptionsBuilder.name(Mockito.any())).thenReturn(recordingOptionsBuilder);
        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(builder.build()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "foo.Bar:enabled=true" });

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(recordingOptionsBuilder).name(nameCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());
        verify(exporter).addRecording(descriptorCaptor.capture());

        String actualName = nameCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();
        IRecordingDescriptor recordingDescriptor = descriptorCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(recordingDescriptor, Matchers.sameInstance(descriptor));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(),Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldStartRecordingOnSerializableExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        when(recordingOptionsBuilder.name(Mockito.any())).thenReturn(recordingOptionsBuilder);
        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(builder.build()).thenReturn(events);
        when(exporter.getDownloadURL(Mockito.anyString())).thenReturn("example-url");

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        Output out = command.serializableExecute(new String[]{ "foo", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(StringOutput.class));
        MatcherAssert.assertThat(((StringOutput) out).getMessage(), Matchers.equalTo("example-url"));

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(recordingOptionsBuilder).name(nameCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());
        verify(exporter).addRecording(descriptorCaptor.capture());

        String actualName = nameCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();
        IRecordingDescriptor recordingDescriptor = descriptorCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(recordingDescriptor, Matchers.sameInstance(descriptor));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(),Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleNameCollisionOnExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "foo.Bar:enabled=true" });

        verify(service).getAvailableRecordings();
        verify(cw).println("Recording with name \"foo\" already exists");

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleNameCollisionOnSerializableExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        Output out = command.serializableExecute(new String[]{ "foo", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(FailureOutput.class));
        MatcherAssert.assertThat(((FailureOutput) out).getMessage(), Matchers.equalTo("Recording with name \"foo\" already exists"));

        verify(service).getAvailableRecordings();

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleExceptionOnSerializableExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        Output out = command.serializableExecute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        MatcherAssert.assertThat(((ExceptionOutput) out).getExceptionMessage(), Matchers.equalTo("NullPointerException: "));
    }

}