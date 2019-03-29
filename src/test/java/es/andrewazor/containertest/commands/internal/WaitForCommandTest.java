package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class WaitForCommandTest extends StdoutTest {

    private WaitForCommand command;
    @Mock
    private JMCConnection connection;
    @Mock
    private IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new WaitForCommand();
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedWaitFor() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait-for"));
    }

    @Test
    void shouldNotExpectZeroArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectTooManyArgs() {
        assertFalse(command.validate(new String[2]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotValidateMalformedRecordingName() {
        assertFalse(command.validate(new String[] { "." }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[] { "foo" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.emptyString());
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] { "foo" });

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout.toString(),
                Matchers.equalTo("Recording with name \"foo\" not found in target JVM\n"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldHandleRecordingIsContinuousAndRunning() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.isContinuous()).thenReturn(true);
        when(descriptor.getState()).thenReturn(RecordingState.RUNNING);
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(descriptor));

        command.execute(new String[] { "foo" });

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout.toString(),
                Matchers.equalTo("Recording \"foo\" is continuous, refusing to wait\n"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldWaitForRecordingToStop() throws Exception {
        IRecordingDescriptor descriptorA = mock(IRecordingDescriptor.class);
        when(descriptorA.getName()).thenReturn("foo");
        when(descriptorA.isContinuous()).thenReturn(false);
        when(descriptorA.getState())
            .thenReturn(RecordingState.RUNNING)
            .thenReturn(RecordingState.RUNNING)
            .thenReturn(RecordingState.STOPPED);
        when(descriptorA.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptorA.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));

        IRecordingDescriptor descriptorB = mock(IRecordingDescriptor.class);
        when(descriptorB.getName()).thenReturn("bar");

        when(descriptorA.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptorA.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));
        when(connection.getApproximateServerTime()).thenReturn(5_000L);
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(descriptorB, descriptorA));

        assertTimeout(Duration.ofSeconds(10), () -> {
            command.execute(new String[] { "foo" });

            verify(connection, Mockito.times(3)).getService();
            verify(service, Mockito.times(3)).getAvailableRecordings();
            // Use byte array constructor due to \b control characters in output
            String s = new String(new byte[]{ 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 46, 32, 46, 32, 46, 32, 46, 32, 46, 8, 10 });
            MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo(s));

            verifyNoMoreInteractions(connection);
            verifyNoMoreInteractions(service);
        });
    }

}