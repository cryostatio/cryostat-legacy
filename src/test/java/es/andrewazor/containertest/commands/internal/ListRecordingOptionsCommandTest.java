package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class ListRecordingOptionsCommandTest extends TestBase {

    private ListRecordingOptionsCommand command;
    @Mock private JMCConnection connection;
    @Mock private IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new ListRecordingOptionsCommand();
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedListRecordingOptions() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-recording-options"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPrintRecordingOptions() throws Exception {
        Map options = new HashMap();
        IOptionDescriptor descriptor = mock(IOptionDescriptor.class);
        when(descriptor.toString()).thenReturn("foo-option-toString");
        options.put("foo-option", descriptor);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordingOptions()).thenReturn(options);

        command.execute(new String[0]);
        MatcherAssert.assertThat(stdout.toString(), Matchers.allOf(
            Matchers.containsString("Available recording options:\n"),
            Matchers.containsString("\tfoo-option : foo-option-toString\n")
        ));
    }

}