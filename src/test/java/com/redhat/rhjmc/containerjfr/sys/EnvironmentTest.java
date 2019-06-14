package com.redhat.rhjmc.containerjfr.sys;

import java.util.UUID;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentTest {

    Environment env;

    @BeforeEach
    void setup() {
        env = new Environment();
    }

    @Test
    void getEnvShouldNotContainRandomUUIDEnv() {
        MatcherAssert.assertThat(env.getEnv(UUID.randomUUID().toString()), Matchers.nullValue());
    }

    @Test
    void getEnvShouldReturnSpecifiedDefaultWhenEnvVarUndefined() {
        MatcherAssert.assertThat(env.getEnv(UUID.randomUUID().toString(), "default"), Matchers.equalTo("default"));
    }

    @Test
    void getPropertyShouldNotContainRandomUUIDProperty() {
        MatcherAssert.assertThat(env.getProperty(UUID.randomUUID().toString()), Matchers.nullValue());
    }

    @Test
    void getPropertyShouldReturnSpecifiedDefaultWhenPropertyUnset() {
        MatcherAssert.assertThat(env.getProperty(UUID.randomUUID().toString(), "default"), Matchers.equalTo("default"));
    }

}