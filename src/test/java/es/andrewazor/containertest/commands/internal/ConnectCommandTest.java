package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collections;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.ConnectionListener;

@ExtendWith(MockitoExtension.class)
class ConnectCommandTest {

    private ConnectCommand command;
    @Mock ConnectionListener listener;

    @BeforeEach
    void setup() {
        command = new ConnectCommand(Collections.singleton(listener));
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldBeNamedConnect() {
        assertThat(command.getName(), Matchers.equalTo("connect"));
    }

    @Test
    void emptyStringIsInvalid() {
        assertFalse(command.validate(new String[]{""}));
    }

    @Test
    void hostnameIsValid() {
        assertTrue(command.validate(new String[]{"some.host"}));
    }

    @Test
    void hostnameWithPortIsValid() {
        assertTrue(command.validate(new String[]{"some.host:8080"}));
    }

    @Test
    void hostnameWithDelimiterButNoPortIsInvalid() {
        assertFalse(command.validate(new String[]{"some.host:"}));
    }

}