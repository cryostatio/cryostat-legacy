package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest {

    private AbstractRecordingCommand command;

    @BeforeEach
    void setup() {
        command = new BaseRecordingCommand();
    }

    @Test
    void emptyStringIsInvalidEventString() {
        assertFalse(command.validateEvents(""));
    }

    @Test
    void corruptStringIsInvalidEventString() {
        assertFalse(command.validateEvents("jdk:bar:baz"));
    }

    @Test
    void eventWithoutPropertyIsInvalid() {
        assertFalse(command.validateEvents("jdk.Event"));
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
