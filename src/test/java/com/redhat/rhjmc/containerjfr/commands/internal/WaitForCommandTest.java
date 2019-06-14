package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.sys.Clock;
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

@ExtendWith(MockitoExtension.class)
class WaitForCommandTest extends TestBase {

    WaitForCommand command;
    @Mock
    JMCConnection connection;
    @Mock IFlightRecorderService service;
    @Mock
    Clock clock;

    @BeforeEach
    void setup() {
        command = new WaitForCommand(mockClientWriter, clock);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedWaitFor() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait-for"));
    }

    @Test
    void shouldNotExpectZeroArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectTooManyArgs() {
        assertFalse(command.validate(new String[2]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotValidateMalformedRecordingName() {
        assertFalse(command.validate(new String[] { "." }));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[] { "foo" }));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] { "foo" });

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout(),
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
        MatcherAssert.assertThat(stdout(),
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
            .thenReturn(RecordingState.RUNNING)
            .thenReturn(RecordingState.RUNNING)
            .thenReturn(RecordingState.STOPPED);
        when(descriptorA.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptorA.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));

        IRecordingDescriptor descriptorB = mock(IRecordingDescriptor.class);
        when(descriptorB.getName()).thenReturn("bar");

        when(descriptorA.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptorA.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));
        when(connection.getApproximateServerTime(clock))
            .thenReturn(5_000L)
            .thenReturn(5_001L)
            .thenReturn(6_000L);
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(descriptorB, descriptorA));

        command.execute(new String[] { "foo" });

        verify(connection, Mockito.times(5)).getService();
        verify(service, Mockito.times(5)).getAvailableRecordings();
        // Use byte array constructor due to \b control characters in output
        String s = new String(
                new byte[] { 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 46, 32, 46, 32, 46, 32, 46, 32, 46, 8, 8, 8, 8, 8, 8, 8,
                        8, 8, 8, 8, 8, 46, 32, 46, 32, 46, 32, 46, 32, 46, 32, 46, 8, 46, 10 }
                        );
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(s));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldExitEarlyForAlreadyStoppedRecordings() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.isContinuous()).thenReturn(true);
        when(descriptor.getState()).thenReturn(RecordingState.STOPPED);
        when(descriptor.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptor.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(descriptor));

        command.execute(new String[] { "foo" });

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("\n"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
    }

}