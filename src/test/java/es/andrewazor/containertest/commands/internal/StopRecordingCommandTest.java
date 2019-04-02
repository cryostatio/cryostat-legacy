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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.net.JMCConnection;

@ExtendWith(MockitoExtension.class)
class StopRecordingCommandTest extends TestBase {

    private StopRecordingCommand command;
    @Mock private JMCConnection connection;
    @Mock private IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new StopRecordingCommand(mockClientWriter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedStop() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("stop"));
    }

    @Test
    void shouldNotExpectNoArg() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument: recording name.\n"));
    }

    @Test
    void shouldNotExpectMalformedArg() {
        assertFalse(command.validate(new String[]{ "." }));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldExpectRecordingNameArg() {
        assertTrue(command.validate(new String[]{ "foo" }));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldHandleNoRecordingFound() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[]{ "foo" });

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Recording with name \"foo\" not found\n"));
    }

    @Test
    void shouldHandleRecordingFound() throws Exception {
        verifyZeroInteractions(service);
        verifyZeroInteractions(connection);

        IRecordingDescriptor fooDescriptor = mock(IRecordingDescriptor.class);
        when(fooDescriptor.getName()).thenReturn("foo");
        IRecordingDescriptor barDescriptor = mock(IRecordingDescriptor.class);
        when(barDescriptor.getName()).thenReturn("bar");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(barDescriptor, fooDescriptor));

        command.execute(new String[]{ "foo" });

        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(service).stop(descriptorCaptor.capture());
        IRecordingDescriptor captured = descriptorCaptor.getValue();
        MatcherAssert.assertThat(captured, Matchers.sameInstance(fooDescriptor));

        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(connection);
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

}