package es.andrewazor.containertest.tui.tty;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoOpClientReaderTest {

    NoOpClientReader clientReader;

    @BeforeEach
    void setup() {
        clientReader = new NoOpClientReader();
    }

    @Test
    void closeShouldDoNothing() {
        assertDoesNotThrow(clientReader::close);
    }

    @Test
    void readLineShouldThrow() {
        Exception e = assertThrows(UnsupportedOperationException.class, clientReader::readLine);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("NoOpClientReader does not support readLine"));
    }

}