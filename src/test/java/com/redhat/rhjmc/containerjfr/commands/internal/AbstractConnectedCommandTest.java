package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@ExtendWith(MockitoExtension.class)
class AbstractConnectedCommandTest {

    AbstractConnectedCommand command;

    @BeforeEach
    void setup() {
        this.command = new BaseConnectedCommand();
    }

    @Nested
    class WithConnection {

        @Mock
        JFRConnection mockConnection;

        @BeforeEach
        void setup() {
            command.connectionChanged(mockConnection);
        }

        @Test
        void shouldBeAvailable() {
            assertTrue(command.isAvailable());
        }

        @Test
        void shouldContainExpectedConnection() throws AbstractConnectedCommand.JMXConnectionException {
            assertThat(command.getConnection(), is(mockConnection));
        }

        @Test
        void shouldGetServiceFromConnection() throws AbstractConnectedCommand.JMXConnectionException {
            IFlightRecorderService mockService = mock(IFlightRecorderService.class);
            when(mockConnection.getService()).thenReturn(mockService);
            verify(mockConnection, never()).getService();
            assertThat(command.getService(), is(mockService));
            verify(mockConnection).getService();
            verifyNoMoreInteractions(mockConnection);
        }

        @Test
        void shouldGetMatchingDescriptorByName() throws Exception {
            IFlightRecorderService mockService = mock(IFlightRecorderService.class);
            IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
            when(mockConnection.getService()).thenReturn(mockService);
            when(mockService.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
            when(recording.getName()).thenReturn("foo");
            Optional<IRecordingDescriptor> descriptor = command.getDescriptorByName("foo");
            assertTrue(descriptor.isPresent());
            MatcherAssert.assertThat(descriptor.get(), Matchers.sameInstance(recording));
        }

        @Test
        void shouldReturnEmptyOptionalIfNoMatchingDescriptorFound() throws Exception {
            IFlightRecorderService mockService = mock(IFlightRecorderService.class);
            IRecordingDescriptor recording = mock(IRecordingDescriptor.class);
            when(mockConnection.getService()).thenReturn(mockService);
            when(mockService.getAvailableRecordings()).thenReturn(Collections.singletonList(recording));
            when(recording.getName()).thenReturn("foo");
            Optional<IRecordingDescriptor> descriptor = command.getDescriptorByName("bar");
            assertFalse(descriptor.isPresent());
        }
    }

    @Nested
    class WithoutConnection {

        @Test
        void shouldNotBeAvailable() {
            assertFalse(command.isAvailable());
        }

        @Test
        void shouldThrowOnGetConnection() {
            AbstractConnectedCommand.JMXConnectionException ex = assertThrows(AbstractConnectedCommand.JMXConnectionException.class, command::getConnection);
            assertThat(ex.getMessage(), equalTo("No active JMX connection"));
        }

        @Test
        void shouldThrowOnGetService() {
            AbstractConnectedCommand.JMXConnectionException ex = assertThrows(AbstractConnectedCommand.JMXConnectionException.class, command::getService);
            assertThat(ex.getMessage(), equalTo("No active JMX connection"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            ".",
            ".jfr",
            "some.other.jfr",
            "/",
            "/a/path",
            "another/path",
            " ",
            "two words",
        })
        void shouldNotValidateMalformedRecordingNames(String name) {
            assertFalse(command.validateRecordingName(name));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "foo",
            "recording",
            "recording.jfr",
            "Capitalized",
            "4lphanum3r1c",
            "with-dash",
            "with_underscore",
        })
        void shouldValidateRecordingNames(String name) {
            assertTrue(command.validateRecordingName(name));
        }

        @Test
        void getDescriptorByNameShouldThrow() throws Exception {
            assertThrows(AbstractConnectedCommand.JMXConnectionException.class, () -> command.getDescriptorByName("someRecording"));
        }
    }

    static class BaseConnectedCommand extends AbstractConnectedCommand {

        @Override
        public String getName() {
            return "base";
        }

        @Override
        public void execute(String[] args) { }

        @Override
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public JFRConnection getConnection() throws JMXConnectionException {
            return super.getConnection();
        }

        @Override
        public IFlightRecorderService getService() throws JMXConnectionException {
            return super.getService();
        }

    }

}
