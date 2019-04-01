package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class ListCommandTest extends TestBase {

    private ListCommand command;
    @Mock private JMCConnection connection;
    @Mock private IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new ListCommand();
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedList() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertTrue(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
    }

    @Test
    void shouldHandleNoRecordings() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        command.execute(new String[0]);
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Available recordings:\n\tNone\n"));
    }

    @Test
    void shouldPrintRecordingNames() throws Exception {
        when(connection.getService()).thenReturn(service);
        List<IRecordingDescriptor> descriptors = Arrays.asList(
            createDescriptor("foo"),
            createDescriptor("bar")
        );
        when(service.getAvailableRecordings()).thenReturn(descriptors);
        command.execute(new String[0]);
        MatcherAssert.assertThat(stdout.toString(), Matchers.allOf(
            Matchers.containsString("Available recordings:\n"),
            Matchers.containsString("getName\t\tfoo\n"),
            Matchers.containsString("getName\t\tbar\n")
        ));
    }

    private static IRecordingDescriptor createDescriptor(String name) {
        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(descriptor.getName()).thenReturn(name);
        return descriptor;
    }

}