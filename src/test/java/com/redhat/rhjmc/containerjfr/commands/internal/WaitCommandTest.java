package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.sys.Clock;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitCommandTest extends TestBase {

    WaitCommand command;
    @Mock
    Clock clock;

    @BeforeEach
    void setup() {
        command = new WaitCommand(mockClientWriter, clock);
    }

    @Test
    void shouldBeNamedWait() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("wait"));
    }

    @Test
    void shouldNotExpectZeroArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldNotExpectTwoArgs() {
        assertFalse(command.validate(new String[2]));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("Expected one argument\n"));
    }

    @Test
    void shouldExpectIntegerFormattedArg() {
        assertFalse(command.validate(new String[]{ "f" }));
        MatcherAssert.assertThat(stdout(), Matchers.equalTo("f is an invalid integer\n"));
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[]{ "10" }));
        MatcherAssert.assertThat(stdout(), Matchers.emptyString());
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void testExecution() throws Exception {
        when(clock.getWallTime()).thenReturn(0L).thenReturn(1_000L).thenReturn(2_000L);
        command.execute(new String[]{ "1" });
        MatcherAssert.assertThat(stdout(), Matchers.equalTo(". \n"));
        verify(clock, Mockito.times(2)).getWallTime();
        verify(clock).sleep(TimeUnit.SECONDS, 1);
    }

}