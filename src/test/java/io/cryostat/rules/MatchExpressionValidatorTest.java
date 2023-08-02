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
package io.cryostat.rules;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchExpressionValidatorTest {

    MatchExpressionValidator validator;
    @Mock Rule rule;

    @BeforeEach
    void setup() {
        this.validator = new MatchExpressionValidator();
        Mockito.when(rule.getName()).thenReturn("mockRule");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "true",
                "target.alias == \"io.cryostat.Cryostat\"",
                "target.alias == 'io.cryostat.Cryostat' || target.annotations.cryostat.JAVA_MAIN =="
                        + " 'io.cryostat.Cryostat'",
                "target.connectUrl != '' && target.labels.SOMETHING == 'other'",
                "taret.jvmId == \"abcd1234\"",
                "taret.jvmId != \"hello world\"",
                "/^[a-z]+$/.test(target.alias)",
                "/^[a-z]+$/.test(target.noSuchProperty)",
                "/^[a-z]+$/.test([].length)",
                "/^[a-z]+$/.test(\"stringLiteral\")",
            })
    void shouldReturnValidExpressions(String expr) throws Exception {
        Mockito.when(rule.getMatchExpression()).thenReturn(expr);
        String result = validator.validate(rule);
        MatcherAssert.assertThat(result, Matchers.equalTo(expr));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "System.exit(1)",
                "while (true) continue;",
                "target.alias.contains('substr')",
                "function foo() { return true; }; foo();",
                "[]()",
                "target.alias.test(\"nonempty\")",
                "/^[a-z]+$/.test({ \"key\": \"value\" })",
                "/^[a-z]+$/.test(1234)",
                "/^[a-z]+$/.test([])",
                "/^[a-z]+$/.test(/^another-regexp$/)",
            })
    void shouldThrowOnIllegalExpressions(String expr) throws Exception {
        Mockito.when(rule.getMatchExpression()).thenReturn(expr);
        Assertions.assertThrows(
                MatchExpressionValidationException.class, () -> validator.validate(rule));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "(() => true)()",
                "target.alias == 'missing close quote",
                "target.alias == missing open quote'",
                "{}()",
            })
    void shouldThrowOnMalformedExpressions(String expr) throws Exception {
        Mockito.when(rule.getMatchExpression()).thenReturn(expr);
        Assertions.assertThrows(
                MatchExpressionValidationException.class, () -> validator.validate(rule));
    }
}
