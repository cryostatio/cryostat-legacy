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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class SetMatcher implements LabelMatcher {

    private final SetMatcher.Operator operator;
    private final String key;
    private final Set<String> values;

    SetMatcher(String key, SetMatcher.Operator operator) {
        this(key, operator, Set.of());
    }

    SetMatcher(String key, SetMatcher.Operator operator, Collection<String> values) {
        this.key = key;
        this.operator = operator;
        this.values = new HashSet<>(values);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public boolean test(String s) {
        return operator.with(values).test(s);
    }

    public enum Operator {
        IN("In", args -> v -> contains(args, v)),
        NOT_IN("NotIn", args -> v -> !contains(args, v)),
        EXISTS("", args -> v -> v != null),
        DOES_NOT_EXIST("!", args -> v -> v == null),
        ;

        private final String token;
        private final Function<Collection<String>, Predicate<String>> fn;

        Operator(String token, Function<Collection<String>, Predicate<String>> fn) {
            this.token = token;
            this.fn = fn;
        }

        Predicate<String> with(Collection<String> values) {
            return fn.apply(values);
        }

        public static Operator fromString(String str) {
            for (Operator op : Operator.values()) {
                if (op.token.equalsIgnoreCase(str)) {
                    return op;
                }
            }
            return null;
        }

        private static boolean contains(Collection<String> args, String v) {
            return args.stream().anyMatch(s -> s.equals(v));
        }
    }
}
