package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

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

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommand.SuccessOutput;
import es.andrewazor.containertest.commands.internal.RecordingOptionsCustomizer.OptionKey;
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
        verify(cw).println("Expected one argument: recording option name");
    }

    @Test
    void shouldNotExpectTooManyArgs() {
        assertFalse(command.validate(new String[2]));
        verify(cw).println("Expected one argument: recording option name");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "foo",
        "+foo",
        "foo=",
        "-foo=bar",
    })
    void shouldNotValidateMalformedArg(String arg) {
        assertFalse(command.validate(new String[]{ arg }));
        verify(cw).println(arg + " is an invalid option string");
    }

    @Test
    void shouldNotValidateUnrecognizedOption() {
        assertFalse(command.validate(new String[] { "someUnknownOption=value" }));
        verify(cw).println("someUnknownOption is an unrecognized or unsupported option");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "toDisk=true",
        "maxAge=10",
        "maxSize=512",
    })
    void shouldKnownValidateKeyValueArg(String arg) {
        assertTrue(command.validate(new String[]{ arg }));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExpectUnsetArg() {
        assertTrue(command.validate(new String[]{ "-toDisk" }));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxAge() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "maxAge=123" });
        verify(customizer).set(OptionKey.MAX_AGE, "123");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxSize() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "maxSize=123" });
        verify(customizer).set(OptionKey.MAX_SIZE, "123");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetToDisk() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "toDisk=true" });
        verify(customizer).set(OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetMaxAge() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "-maxAge" });
        verify(customizer).unset(OptionKey.MAX_AGE);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetMaxSize() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "-maxSize" });
        verify(customizer).unset(OptionKey.MAX_SIZE);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetToDisk() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[]{ "-toDisk" });
        verify(customizer).unset(OptionKey.TO_DISK);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldReturnSuccessOutput() throws Exception {
        verifyZeroInteractions(customizer);
        Output out = command.serializableExecute(new String[]{ "toDisk=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(SuccessOutput.class));
        verify(customizer).set(OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        verifyZeroInteractions(customizer);
        doThrow(NullPointerException.class).when(customizer).set(Mockito.any(), Mockito.any());
        Output out = command.serializableExecute(new String[]{ "toDisk=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(ExceptionOutput.class));
        MatcherAssert.assertThat(((ExceptionOutput) out).getExceptionMessage(), Matchers.equalTo("NullPointerException: "));
        verify(customizer).set(OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
    }

}