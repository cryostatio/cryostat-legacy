/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net.web.http.api.v2.graph.labels;

import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabelSelectorMatcherTest {

    private static final Map<String, String> TEST_LABELS =
            Map.of(
                    "foo", "bar",
                    "something", "else",
                    "my.prefixed/label", "expectedValue",
                    "env", "prod",
                    "present", "irrelevant");

    @ParameterizedTest
    @CsvSource({
        "foo=bar, true",
        "something=wrong, false",
        "my.prefixed/label = expectedValue, true"
    })
    void testSingleEquality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "foo==bar, true",
        "something==wrong, false",
        "my.prefixed/label == expectedValue, true"
    })
    void testDoubleEquality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "foo!=bar, false",
        "something!=wrong, true",
        "my.prefixed/label != expectedValue, false"
    })
    void testInequality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "foo in (bar, baz) : true",
                "something In (else, orother) : true",
                "env IN (stage,qa) : false",
            },
            delimiter = ':')
    void testSetIn(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "foo notin (bar, baz) : false",
                "something NotIn (orother, else, third) : false",
                "env NOTIN (stage,qa) : true",
            },
            delimiter = ':')
    void testSetNotIn(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "foo, true",
        "something, true",
        "my.prefixed/label, true",
        "another/missing-label, false",
        "present, true"
    })
    void testExists(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({"!foo, false", "!something, false", "!present, false"})
    void testNotExists(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(TEST_LABELS), Matchers.is(pass));
    }
}
