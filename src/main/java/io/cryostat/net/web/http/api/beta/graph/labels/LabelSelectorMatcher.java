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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LabelSelectorMatcher implements Predicate<Map<String, String>> {

    // ex. "my.prefix/label = something". Whitespaces around the operator are ignored. Left side
    // must loosely look like a k8s label (not strictly enforced here), right side must loosely look
    // like a k8s label value, which may be empty. Allowed operators are "=", "==", "!=".
    static final Pattern EQUALITY_PATTERN =
            Pattern.compile(
                    "^(?<key>[\\S]+)[\\s]*(?<op>=|==|!=)[\\s]*(?<value>[\\S]*)$");

    // ex. "environment in (production, qa)" or "tier NotIn (frontend, backend)". Tests if the given
    // label has or does not have any of the specified values.
    // FIXME the part of the expression to the right of the operator actually allows mismatched
    // parens, not a single matched pair. Regexes are hard. This works but is too tolerant of broken
    // expressions.
    static final Pattern SET_MEMBER_PATTERN =
            Pattern.compile("(?<key>[\\S+]+)[\\s]+(?<op>in|notin)[\\s]+\\((?<values>(?:[\\S]+[,\\s]*)+)\\)",
                    Pattern.CASE_INSENSITIVE);

    // ex. "mykey" or "!mykey". Tests whether the given key name exists in the test label set as a
    // key, with or without a value.
    static final Pattern SET_EXISTENCE_PATTERN =
            Pattern.compile("^(?<op>!?)(?<key>[a-zA-Z0-9-_./]+)$");

    private final List<LabelMatcher> matchers = new ArrayList<>();

    private LabelSelectorMatcher(Collection<LabelMatcher> matchers) {
        this.matchers.addAll(matchers);
    }

    @Override
    public boolean test(Map<String, String> labels) {
        return this.matchers.stream().allMatch(m -> m.test(labels.get(m.getKey())));
    }

    public static LabelSelectorMatcher parse(String s) throws IllegalArgumentException {
        List<LabelMatcher> matchers = new ArrayList<>();
        if (s != null) {
            List<String> clauses =
                    Arrays.asList(s.split(",")).stream()
                            .map(String::trim)
                            .collect(Collectors.toList());
            matchers.addAll(parseEqualities(clauses));
            matchers.addAll(parseSetMemberships(clauses));
        }
        return new LabelSelectorMatcher(matchers);
    }

    private static Collection<LabelMatcher> parseEqualities(Collection<String> clauses) {
        List<LabelMatcher> matchers = new ArrayList<>();
        for (String clause : clauses) {
            Matcher m = EQUALITY_PATTERN.matcher(clause);
            if (!m.matches()) {
                continue;
            }
            String key = m.group("key");
            String op = m.group("op");
            EqualityMatcher.Operator operator = EqualityMatcher.Operator.fromString(op);
            Objects.requireNonNull(operator, "Unknown equality operator " + op);
            String value = m.group("value");
            EqualityMatcher em = new EqualityMatcher(key, operator, value);
            matchers.add(em);
        }
        return matchers;
    }

    private static Collection<LabelMatcher> parseSetMemberships(Collection<String> clauses) {
        List<LabelMatcher> matchers = new ArrayList<>();
        for (String clause : clauses) {
            Matcher m = SET_MEMBER_PATTERN.matcher(clause);
            if (!m.matches()) {
                continue;
            }
            String key = m.group("key");
            String op = m.group("op");
            SetMatcher.Operator operator = SetMatcher.Operator.fromString(op);
            Objects.requireNonNull(operator, "Unknown set operator " + op);
            String value = m.group("values");
            List<String> values =
                    Arrays.asList(value.split(",")).stream()
                            .map(String::trim)
                            .collect(Collectors.toList());
            SetMatcher em = new SetMatcher(key, operator, values);
            matchers.add(em);
        }
        for (String clause : clauses) {
            Matcher m = SET_EXISTENCE_PATTERN.matcher(clause);
            if (!m.matches()) {
                continue;
            }
            String key = m.group("key");
            String op = m.group("op");
            SetMatcher.Operator operator = SetMatcher.Operator.fromString(op);
            Objects.requireNonNull(operator, "Unknown set operator " + op);
            SetMatcher em = new SetMatcher(key, operator);
            matchers.add(em);
        }
        return matchers;
    }
}
