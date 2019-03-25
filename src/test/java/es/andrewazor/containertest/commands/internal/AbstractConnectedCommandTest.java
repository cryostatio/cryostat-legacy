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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.internal.AbstractConnectedCommand.JMXConnectionException;

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