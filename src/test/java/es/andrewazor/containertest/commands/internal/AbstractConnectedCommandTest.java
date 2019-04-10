package es.andrewazor.containertest.commands.internal;

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

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.commands.internal.AbstractConnectedCommand.JMXConnectionException;
import es.andrewazor.containertest.net.JMCConnection;

class AbstractConnectedCommandTest {

    @ExtendWith(MockitoExtension.class)
    static class WithConnection {

        private AbstractConnectedCommand command;
        @Mock private JMCConnection mockConnection;

        @BeforeEach
        void setup() {
            this.command = new BaseConnectedCommand();
            command.connectionChanged(mockConnection);
        }

        @Test
        void shouldBeAvailable() {
            assertTrue(command.isAvailable());
        }

        @Test
        void shouldContainExpectedConnection() throws JMXConnectionException {
            assertThat(command.getConnection(), is(mockConnection));
        }

        @Test
        void shouldGetServiceFromConnection() throws JMXConnectionException {
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

    @ExtendWith(MockitoExtension.class)
    static class WithoutConnection {

        private AbstractConnectedCommand command;

        @BeforeEach
        void setup() {
            this.command = new BaseConnectedCommand();
        }

        @Test
        void shouldNotBeAvailable() {
            assertFalse(command.isAvailable());
        }

        @Test
        void shouldThrowOnGetConnection() {
            JMXConnectionException ex = assertThrows(JMXConnectionException.class, command::getConnection);
            assertThat(ex.getMessage(), equalTo("No active JMX connection"));
        }

        @Test
        void shouldThrowOnGetService() {
            JMXConnectionException ex = assertThrows(JMXConnectionException.class, command::getService);
            assertThat(ex.getMessage(), equalTo("No active JMX connection"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            ".",
            "extension.jfr",
            "/",
            "/a/path",
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
            "Capitalized",
            "4lphanum3r1c"
        })
        void shouldValidateRecordingNames(String name) {
            assertTrue(command.validateRecordingName(name));
        }

        @Test
        void getDescriptorByNameShouldThrow() throws Exception {
            assertThrows(JMXConnectionException.class, () -> command.getDescriptorByName("someRecording"));
        }
    }

    private static class BaseConnectedCommand extends AbstractConnectedCommand {

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
        public JMCConnection getConnection() throws JMXConnectionException {
            return super.getConnection();
        }

        @Override
        public IFlightRecorderService getService() throws JMXConnectionException {
            return super.getService();
        }

    }

}