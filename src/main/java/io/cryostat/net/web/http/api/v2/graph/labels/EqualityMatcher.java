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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class EqualityMatcher implements LabelMatcher {

    private final String key;
    private final EqualityMatcher.Operator operator;
    private final String value;

    EqualityMatcher(String key, EqualityMatcher.Operator operator, String value) {
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public boolean test(String s) {
        return operator.with(value).test(s);
    }

    public enum Operator {
        EQUAL("=", arg -> v -> Objects.equals(arg, v)),
        DOUBLE_EQUAL("==", arg -> v -> Objects.equals(arg, v)),
        NOT_EQUAL("!=", arg -> v -> !Objects.equals(arg, v)),
        ;

        private final String token;
        private final Function<String, Predicate<String>> fn;

        Operator(String token, Function<String, Predicate<String>> fn) {
            this.token = token;
            this.fn = fn;
        }

        Predicate<String> with(String value) {
            return fn.apply(value);
        }

        public static Operator fromString(String str) {
            for (Operator op : Operator.values()) {
                if (op.token.equals(str)) {
                    return op;
                }
            }
            return null;
        }
    }
}
