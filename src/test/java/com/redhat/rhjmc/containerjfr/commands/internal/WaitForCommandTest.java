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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class WaitForCommandTest extends TestBase implements ValidatesRecordingName, ValidatesTargetId {

    WaitForCommand command;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock Clock clock;

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
        command = new WaitForCommand(mockClientWriter, targetConnectionManager, clock);
    }

    @Test
    void shouldBeNamedWaitFor() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait-for"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void shouldNotValidateIncorrectArgc(int argc) {
        Exception e =
                assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[argc]));
        String errorMessage =
                "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name";
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(errorMessage + '\n'));
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldValidateArgs() {
        assertDoesNotThrow(() -> command.validate(new String[] {"fooHost:9091", "someRecording"}));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldHandleRecordingNotFound() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(
                stdout(),
                Matchers.equalTo("Recording with name \"foo\" not found in target JVM\n"));
    }

    @Test
    void shouldHandleRecordingIsContinuousAndRunning() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.isContinuous()).thenReturn(true);
        when(descriptor.getState()).thenReturn(RecordingState.RUNNING);
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(descriptor));

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(
                stdout(), Matchers.equalTo("Recording \"foo\" is continuous, refusing to wait\n"));
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
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(descriptorB, descriptorA));

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(connection, Mockito.atLeastOnce()).getService();
        verify(service, Mockito.times(5)).getAvailableRecordings();
        // Use byte array constructor due to \b control characters in output
        String s =
                new String(
                        new byte[] {
                            8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 46, 32, 46, 32, 46, 32, 46, 32, 46, 8, 8,
                            8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 46, 32, 46, 32, 46, 32, 46, 32, 46, 32,
                            46, 8, 46, 10
                        });
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(s));
    }

    @Test
    void shouldExitEarlyForAlreadyStoppedRecordings() throws Exception {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn("foo");
        when(descriptor.isContinuous()).thenReturn(true);
        when(descriptor.getState()).thenReturn(RecordingState.STOPPED);
        when(descriptor.getDataStartTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(0));
        when(descriptor.getDataEndTime()).thenReturn(UnitLookup.EPOCH_MS.quantity(10_000));
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.singletonList(descriptor));

        command.execute(new String[] {"fooHost:9091", "foo"});

        verify(connection).getService();
        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("\n"));
    }
}
