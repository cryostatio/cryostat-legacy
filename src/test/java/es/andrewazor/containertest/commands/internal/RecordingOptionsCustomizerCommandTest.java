package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class RecordingOptionsCustomizerCommandTest {

    RecordingOptionsCustomizerCommand command;
    @Mock ClientWriter cw;
    @Mock RecordingOptionsCustomizer customizer;

    @BeforeEach
    void setup() {
        command = new RecordingOptionsCustomizerCommand(cw, customizer);
    }

    @Test
    void shouldBeNamedRecordingOptions() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("recording-option"));
    }

    @Test
    void shouldNotExpectNoArgs() {
        assertFalse(command.validate(new String[0]));
    }

    @Test
    void shouldNotExpectTooManyArgs() {
        assertFalse(command.validate(new String[2]));
    }

    @Test
    void shouldNotExpectMalformedArgs() {
        assertFalse(command.validate(new String[]{ "foo" }));
        verify(cw).println("foo is an invalid events pattern");
    }

    @Test
    void shouldExpectKeyValueArg() {
        assertTrue(command.validate(new String[]{ "foo=bar" }));
    }

    @Test
    void shouldSetCompression() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "destinationCompressed=true" });
        verify(customizer).destinationCompressed(true);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxAge() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "maxAge=123" });
        verify(customizer).maxAge(123);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxSize() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "maxSize=123" });
        verify(customizer).maxSize(123);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetToDisk() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "toDisk=true" });
        verify(customizer).toDisk(true);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetDestinationFile() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "destinationFile=foo" });
        verify(customizer).destinationFile("foo");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldThrowOnUnrecognizedOption() throws Exception {
        Exception e = assertThrows(UnsupportedOperationException.class,
                () -> command.execute(new String[] { "foo=true" }));
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("foo"));
    }

}