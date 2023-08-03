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

import org.openjdk.nashorn.api.tree.CompilationUnitTree;
import org.openjdk.nashorn.api.tree.Parser;
import org.openjdk.nashorn.api.tree.TreeVisitor;
import org.openjdk.nashorn.internal.runtime.ParserException;

public class MatchExpressionValidator {

    private final Parser parser = Parser.create();

    private final TreeVisitor<Void, String> treeVisitor = new MatchExpressionTreeVisitor();

    public String validate(String matchExpression) throws MatchExpressionValidationException {
        return validate("", matchExpression);
    }

    String validate(Rule rule) throws MatchExpressionValidationException {
        String name = rule.getName();
        return validate(name == null ? "" : name, rule.getMatchExpression());
    }

    private String validate(String name, String matchExpression)
            throws MatchExpressionValidationException {
        try {
            CompilationUnitTree cut = parser.parse(name, matchExpression, null);
            if (cut == null) {
                throw new IllegalMatchExpressionException();
            }
            cut.accept(treeVisitor, matchExpression);
        } catch (ParserException pe) {
            throw new MatchExpressionValidationException(pe);
        } catch (IllegalMatchExpressionException imee) {
            throw new MatchExpressionValidationException(imee);
        }
        return matchExpression;
    }
}
