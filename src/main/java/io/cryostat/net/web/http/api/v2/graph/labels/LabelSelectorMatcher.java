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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LabelSelectorMatcher implements Predicate<Map<String, String>> {

    // ex. "my.prefix/label = something". Whitespaces around the operator are ignored. Left side
    // must loosely look like a k8s label (not strictly enforced here), right side must loosely look
    // like a k8s label value, which may be empty. Allowed operators are "=", "==", "!=".
    static final Pattern EQUALITY_PATTERN =
            Pattern.compile("^(?<key>[^!=\\s]+)\\s*(?<op>=|==|!=)\\s*(?<value>[^!=\\s]*)$");

    // ex. "environment in (production, qa)" or "tier NotIn (frontend, backend)". Tests if the given
    // label has or does not have any of the specified values.
    static final Pattern SET_MEMBER_PATTERN =
            Pattern.compile(
                    "(?<key>\\S+)\\s+(?<op>in|notin)\\s+\\((?<values>.+)\\)",
                    Pattern.CASE_INSENSITIVE);

    // ex. "mykey" or "!mykey". Tests whether the given key name exists in the test label set as a
    // key, with or without a value.
    static final Pattern SET_EXISTENCE_PATTERN =
            Pattern.compile("^(?<op>!?)(?<key>\\S+)$", Pattern.MULTILINE);

    private final List<LabelMatcher> matchers = new ArrayList<>();

    private LabelSelectorMatcher() {
        this(List.of());
    }

    private LabelSelectorMatcher(Collection<LabelMatcher> matchers) {
        this.matchers.addAll(matchers);
    }

    @Override
    public boolean test(Map<String, String> labels) {
        return this.matchers.stream().allMatch(m -> m.test(labels.get(m.getKey())));
    }

    public static LabelSelectorMatcher parse(String clause) throws IllegalArgumentException {
        Collection<Function<String, LabelMatcher>> parsers =
                Arrays.asList(
                        LabelSelectorMatcher::parseEqualities,
                        LabelSelectorMatcher::parseSetMemberships,
                        LabelSelectorMatcher::parseSetExistences);
        for (var parser : parsers) {
            LabelMatcher matcher = parser.apply(clause);
            if (matcher != null) {
                return new LabelSelectorMatcher(List.of(matcher));
            }
        }
        return new LabelSelectorMatcher();
    }

    private static LabelMatcher parseEqualities(String clause) {
        Matcher m = EQUALITY_PATTERN.matcher(clause);
        if (!m.matches()) {
            return null;
        }
        String key = m.group("key");
        String op = m.group("op");
        EqualityMatcher.Operator operator = EqualityMatcher.Operator.fromString(op);
        Objects.requireNonNull(operator, "Unknown equality operator " + op);
        String value = m.group("value");
        return new EqualityMatcher(key, operator, value);
    }

    private static LabelMatcher parseSetMemberships(String clause) {
        Matcher m = SET_MEMBER_PATTERN.matcher(clause);
        if (!m.matches()) {
            return null;
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
        return new SetMatcher(key, operator, values);
    }

    private static LabelMatcher parseSetExistences(String clause) {
        Matcher m = SET_EXISTENCE_PATTERN.matcher(clause);
        if (!m.matches()) {
            return null;
        }
        String key = m.group("key");
        String op = m.group("op");
        SetMatcher.Operator operator = SetMatcher.Operator.fromString(op);
        Objects.requireNonNull(operator, "Unknown set operator " + op);
        return new SetMatcher(key, operator);
    }
}
