package com.redhat.rhjmc.containerjfr.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SerializableCommandTest {

    @Nested
    class SuccessOutputTest {
        SerializableCommand.SuccessOutput out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.SuccessOutput();
        }

        @Test
        void shouldContainNullPayload() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.nullValue());
        }
    }

    @Nested
    class StringOutputTest {
        SerializableCommand.StringOutput out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.StringOutput("foo");
        }

        @Test
        void shouldContainExpectedMessage() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("foo"));
        }
    }

    @Nested
    class ListOutputTest {
        SerializableCommand.ListOutput<String> out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.ListOutput<>(Collections.singletonList("foo"));
        }

        @Test
        void shouldContainExpectedData() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo(Collections.singletonList("foo")));
        }
    }

    @Nested
    class MapOutputTest {
        SerializableCommand.MapOutput<String, Integer> out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.MapOutput<>(Map.of("foo", 5));
        }

        @Test
        void shouldContainExpectedData() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo(Map.of("foo", 5)));
        }
    }

    @Nested
    class ExceptionOutputTest {
        SerializableCommand.ExceptionOutput out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.ExceptionOutput(new IOException("for testing reasons"));
        }

        @Test
        void shouldContainExpectedMessage() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("IOException: for testing reasons"));
        }
    }

    @Nested
    class FailureOutputTest {
        SerializableCommand.FailureOutput out;

        @BeforeEach
        void setup() {
            out = new SerializableCommand.FailureOutput("for testing reasons");
        }

        @Test
        void shouldContainExpectedMessage() {
            MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("for testing reasons"));
        }
    }

}