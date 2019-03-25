package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest {

    private AbstractRecordingCommand command;
    private PrintStream origOut;
    private ByteArrayOutputStream stdout;

    @BeforeEach
    void setup() {
        command = new BaseRecordingCommand();
        origOut = System.out;
        stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
    }

    @AfterEach
    void resetOut() {
        System.setOut(origOut);
    }

    @Test
    void emptyStringIsInvalidEventString() {
        assertFalse(command.validateEvents(""));
        assertThat(stdout.toString(), equalTo(" is an invalid events pattern\n"));
    }

    @Test
    void corruptStringIsInvalidEventString() {
        assertFalse(command.validateEvents("jdk:bar:baz"));
        assertThat(stdout.toString(), equalTo("jdk:bar:baz is an invalid events pattern\n"));
    }

    @Test
    void eventWithoutPropertyIsInvalid() {
        assertFalse(command.validateEvents("jdk.Event"));
        assertThat(stdout.toString(), equalTo("jdk.Event is an invalid events pattern\n"));
    }

    @Test
    void singleEventStringIsValid() {
        assertTrue(command.validateEvents("foo.Event:prop=val"));
    }

    @Test
    void multipleEventStringIsValid() {
        assertTrue(command.validateEvents("foo.Event:prop=val,bar.Event:thing=1"));
    }

    private static class BaseRecordingCommand extends AbstractRecordingCommand {
        @Override
        public String getName() {
            return "base";
        }

        @Override
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public void execute(String[] args) { }
    }
}
