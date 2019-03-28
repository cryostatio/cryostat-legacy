package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class WaitCommandTest extends StdoutTest {

    private WaitCommand command;

    @BeforeEach
    void setup() {
        command = new WaitCommand();
    }

    @Test
    void shouldBeNamedWait() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait"));
    }

    @Test
    void shouldNotExpectZeroArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectTwoArgs() {
        assertFalse(command.validate(new String[2]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldExpectIntegerFormattedArg() {
        assertFalse(command.validate(new String[]{ "f" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("f is an invalid integer\n"));
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[]{ "10" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.emptyString());
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void testShortExecution() throws Exception {
        assertTimeout(Duration.ofSeconds(10), () -> {
            long start = System.nanoTime();
            command.execute(new String[]{ "1" });
            long end = System.nanoTime();
            long elapsed = end - start;
            MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo(". . \n"));
            MatcherAssert.assertThat(elapsed, Matchers.greaterThan(TimeUnit.SECONDS.toNanos(1)));
        });
    }

}