/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.net.web.http.api.beta.graph.labels;

import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabelSelectorMatcherTest {

    final Map<String, String> labels = Map.of(
            "foo", "bar",
            "something", "else",
            "my.prefixed/label", "expectedValue",
            "env", "prod",
            "present", "irrelevant"
            );

    @ParameterizedTest
    @CsvSource(value = {
        "foo=bar,something=else,env=prod : true",
        "foo=bar,something=wrong : false",
        ": true",
        ",: true",
        "my.prefixed/label = expectedValue, present, env in ( prod, stage ) : true"
    }, delimiter = ':')
    void testCombos(String expr, boolean pass) {
        if (expr == null) {
            expr = "";
        }
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }


    @ParameterizedTest
    @CsvSource({
        "foo=bar, true",
        "something=wrong, false",
        "my.prefixed/label = expectedValue, true"
    })
    void testSingleEquality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "foo==bar, true",
        "something==wrong, false",
        "my.prefixed/label == expectedValue, true"
    })
    void testDoubleEquality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "foo!=bar, false",
        "something!=wrong, true",
        "my.prefixed/label != expectedValue, false"
    })
    void testInequality(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "foo in (bar, baz) : true",
        "something In (else, orother) : true",
        "env IN (stage,qa) : false",
    }, delimiter = ':')
    void testSetIn(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "foo notin (bar, baz) : false",
        "something NotIn (else, orother) : false",
        "env NOTIN (stage,qa) : true",
    }, delimiter = ':')
    void testSetNotIn(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
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
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

    @ParameterizedTest
    @CsvSource({
        "!foo, false",
        "!something, false",
        "!present, false"
    })
    void testNotExists(String expr, boolean pass) {
        LabelSelectorMatcher matcher = LabelSelectorMatcher.parse(expr);
        MatcherAssert.assertThat(expr, matcher.test(labels), Matchers.is(pass));
    }

}
