/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.commands;

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
            MatcherAssert.assertThat(
                    out.getPayload(), Matchers.equalTo(Collections.singletonList("foo")));
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
            MatcherAssert.assertThat(
                    out.getPayload(), Matchers.equalTo("IOException: for testing reasons"));
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
