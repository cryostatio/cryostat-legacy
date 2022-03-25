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
