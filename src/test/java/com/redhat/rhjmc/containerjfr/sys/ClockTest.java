package com.redhat.rhjmc.containerjfr.sys;

import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClockTest {

    Clock clock;

    @BeforeEach
    void setup() {
        clock = new Clock();
    }

    @Test
    void wallTimeShouldBeCloseToSystem() {
        long clockTime = clock.getWallTime();
        long systemTime = System.currentTimeMillis();
        MatcherAssert.assertThat(Double.valueOf(clockTime), Matchers.closeTo(systemTime, TimeUnit.SECONDS.toMillis(1)));
    }

    @Test
    void monotonicTimeShouldBeCloseToSystem() {
        long clockTime = clock.getMonotonicTime();
        long systemTime = System.nanoTime();
        MatcherAssert.assertThat(Double.valueOf(clockTime), Matchers.closeTo(systemTime, TimeUnit.SECONDS.toNanos(1)));
    }

    @Test
    void sleepShouldSleep() {
        long start = System.nanoTime();
        assertTimeout(Duration.ofSeconds(2), () -> clock.sleep(100));
        long elapsed = System.nanoTime() - start;
        MatcherAssert.assertThat(elapsed, Matchers.greaterThanOrEqualTo(100L));
    }

}