package es.andrewazor.containertest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.rjmx.internal.WrappedConnectionException;

@ExtendWith(MockitoExtension.class)
class JMCConnectionToolkitTest {

    JMCConnectionToolkit toolkit;

    @BeforeEach
    void setup() {
        toolkit = new JMCConnectionToolkit();
    }

    @Test
    void shouldThrowInTestEnvironment() {
        assertThrows(WrappedConnectionException.class, () -> toolkit.connect("foo", 9091));
    }

    @Test
    void shouldThrowInTestEnvironment2() {
        assertThrows(WrappedConnectionException.class, () -> toolkit.connect("foo"));
    }

}