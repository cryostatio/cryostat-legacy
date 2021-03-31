/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.rules;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleTest {

    static final String NAME = "fooRule";
    static final String TARGET_ALIAS = "someAlias";
    static final String EVENT_SPECIFIER = "template=Something";

    Rule.Builder builder;

    @BeforeEach
    void setup() {
        builder = new Rule.Builder();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldThrowOnBlankName(String s) {
        IllegalArgumentException ex =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            builder.name(s)
                                    .targetAlias(TARGET_ALIAS)
                                    .eventSpecifier(EVENT_SPECIFIER)
                                    .build();
                        });
        MatcherAssert.assertThat(
                ex.getMessage(),
                Matchers.containsString("\"name\" cannot be blank, was \"" + s + "\""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldThrowOnBlankTargetAlias(String s) {
        IllegalArgumentException ex =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            builder.name(NAME)
                                    .targetAlias(s)
                                    .eventSpecifier(EVENT_SPECIFIER)
                                    .build();
                        });
        MatcherAssert.assertThat(
                ex.getMessage(),
                Matchers.containsString("\"targetAlias\" cannot be blank, was \"" + s + "\""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldThrowOnBlankEventSpecifier(String s) {
        IllegalArgumentException ex =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            builder.name(NAME).targetAlias(TARGET_ALIAS).eventSpecifier(s).build();
                        });
        MatcherAssert.assertThat(
                ex.getMessage(),
                Matchers.containsString("\"eventSpecifier\" cannot be blank, was \"" + s + "\""));
    }

    @Test
    void shouldThrowOnNegativeArchivalPeriod() {
        IllegalArgumentException ex =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            builder.name(NAME)
                                    .targetAlias(TARGET_ALIAS)
                                    .eventSpecifier(EVENT_SPECIFIER)
                                    .archivalPeriodSeconds(-1)
                                    .build();
                        });
        MatcherAssert.assertThat(
                ex.getMessage(),
                Matchers.containsString(
                        "\"archivalPeriodSeconds\" cannot be negative, was \"-1\""));
    }

    @Test
    void shouldThrowOnNegativePreservedArchives() {
        IllegalArgumentException ex =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            builder.name(NAME)
                                    .targetAlias(TARGET_ALIAS)
                                    .eventSpecifier(EVENT_SPECIFIER)
                                    .preservedArchives(-1)
                                    .build();
                        });
        MatcherAssert.assertThat(
                ex.getMessage(),
                Matchers.containsString("\"preservedArchives\" cannot be negative, was \"-1\""));
    }

    @Test
    void shouldDefaultToEmptyDescriptionIfLeftNull() {
        Rule rule =
                builder.name(NAME)
                        .targetAlias(TARGET_ALIAS)
                        .eventSpecifier(EVENT_SPECIFIER)
                        .build();
        MatcherAssert.assertThat(rule.getDescription(), Matchers.is(Matchers.emptyString()));
    }

    @Test
    void shouldSanitizeName() {
        Rule rule =
                builder.name("Some Rule")
                        .targetAlias(TARGET_ALIAS)
                        .eventSpecifier(EVENT_SPECIFIER)
                        .build();
        MatcherAssert.assertThat(rule.getName(), Matchers.equalTo("Some_Rule"));
    }

    @Test
    void shouldSanitizeRecordingNameAndMarkAsAutomatic() {
        Rule rule =
                builder.name("Some Rule")
                        .targetAlias(TARGET_ALIAS)
                        .eventSpecifier(EVENT_SPECIFIER)
                        .build();
        MatcherAssert.assertThat(rule.getRecordingName(), Matchers.equalTo("auto_Some_Rule"));
    }
}
