package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.RecordingExporter;
import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class DumpCommandTest extends StdoutTest {

    private DumpCommand command;
    @Mock private RecordingExporter exporter;
    @Mock private JMCConnection connection;

    @BeforeEach
    void setup() {
        command = new DumpCommand(exporter);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDump() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("dump"));
    }

    @Test
    void shouldPrintArgMessageWhenArgcInvalid() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected three arguments: recording name, recording length, and event types.\n"));
    }

    @Test
    void shouldPrintMessageWhenRecordingNameInvalid() {
        assertFalse(command.validate(new String[]{".", "30", "foo.Bar:enabled=true"}));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldPrintMessageWhenRecordingLengthInvalid() {
        assertFalse(command.validate(new String[]{"recording", "nine", "foo.Bar:enabled=true"}));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("nine is an invalid recording length\n"));
    }

    @Test
    void shouldPrintMessageWhenEventStringInvalid() {
        assertFalse(command.validate(new String[]{"recording", "30", "foo.Bar:=true"}));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("foo.Bar:=true is an invalid events pattern\n"));
    }

    @Test
    void shouldValidateCorrectArgs() {
        assertTrue(command.validate(new String[]{"recording", "30", "foo.Bar:enabled=true"}));
        MatcherAssert.assertThat(stdout.toString(), Matchers.emptyString());
    }

}