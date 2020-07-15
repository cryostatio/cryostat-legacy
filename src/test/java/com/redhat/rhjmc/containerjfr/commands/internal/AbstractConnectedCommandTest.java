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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.TestException;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class AbstractConnectedCommandTest {

    AbstractConnectedCommand command;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;

    @BeforeEach
    void setup() {
        this.command = new BaseConnectedCommand(targetConnectionManager);
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldGetMatchingDescriptorByName() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IFlightRecorderService mockService = mock(IFlightRecorderService.class);
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(connection.getService()).thenReturn(mockService);
        when(mockService.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        when(recording.getName()).thenReturn("foo");
        Optional<IRecordingDescriptor> descriptor =
                command.getDescriptorByName("fooHost:9091", "foo");
        assertTrue(descriptor.isPresent());
        MatcherAssert.assertThat(descriptor.get(), Matchers.sameInstance(recording));
    }

    @Test
    void shouldReturnEmptyOptionalIfNoMatchingDescriptorFound() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        IFlightRecorderService mockService = mock(IFlightRecorderService.class);
        IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
        when(connection.getService()).thenReturn(mockService);
        when(mockService.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
        when(recording.getName()).thenReturn("foo");
        Optional<IRecordingDescriptor> descriptor =
                command.getDescriptorByName("fooHost:9091", "bar");
        assertFalse(descriptor.isPresent());
    }

    @Test
    void shouldThrowIfConnectionManagerThrows() throws Exception {
        when(targetConnectionManager.executeConnectedTask(Mockito.anyString(), Mockito.any()))
                .thenThrow(TestException.class);
        Assertions.assertThrows(
                TestException.class,
                () -> {
                    command.getDescriptorByName("fooHost:9091", "bar");
                });
    }

    static class BaseConnectedCommand extends AbstractConnectedCommand {

        BaseConnectedCommand(TargetConnectionManager targetConnectionManager) {
            super(targetConnectionManager);
        }

        @Override
        public String getName() {
            return "base";
        }

        @Override
        public void execute(String[] args) {}

        @Override
        public void validate(String[] args) throws FailedValidationException {}
    }
}
