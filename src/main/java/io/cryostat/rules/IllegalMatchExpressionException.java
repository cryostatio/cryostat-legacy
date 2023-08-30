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

import org.openjdk.nashorn.api.tree.Tree;

class IllegalMatchExpressionException extends RuntimeException {
    IllegalMatchExpressionException() {
        this("matchExpression parsing failed");
    }

    IllegalMatchExpressionException(String reason) {
        super(reason);
    }

    IllegalMatchExpressionException(Tree node, String matchExpression) {
        super(
                String.format(
                        "matchExpression rejected, illegal %s at [%d, %d]: %s",
                        node.getKind(),
                        node.getStartPosition(),
                        node.getEndPosition(),
                        matchExpression.substring(
                                (int) node.getStartPosition(), (int) node.getEndPosition())));
    }
}
