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
package io.cryostat.rules;

import java.io.IOException;
import java.io.StringReader;

import jdk.nashorn.api.tree.BreakTree;
import jdk.nashorn.api.tree.CaseTree;
import jdk.nashorn.api.tree.CatchTree;
import jdk.nashorn.api.tree.ClassDeclarationTree;
import jdk.nashorn.api.tree.ClassExpressionTree;
import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.ContinueTree;
import jdk.nashorn.api.tree.DebuggerTree;
import jdk.nashorn.api.tree.DoWhileLoopTree;
import jdk.nashorn.api.tree.ErroneousTree;
import jdk.nashorn.api.tree.ExportEntryTree;
import jdk.nashorn.api.tree.ForInLoopTree;
import jdk.nashorn.api.tree.ForLoopTree;
import jdk.nashorn.api.tree.ForOfLoopTree;
import jdk.nashorn.api.tree.FunctionCallTree;
import jdk.nashorn.api.tree.FunctionDeclarationTree;
import jdk.nashorn.api.tree.FunctionExpressionTree;
import jdk.nashorn.api.tree.ImportEntryTree;
import jdk.nashorn.api.tree.InstanceOfTree;
import jdk.nashorn.api.tree.LabeledStatementTree;
import jdk.nashorn.api.tree.ModuleTree;
import jdk.nashorn.api.tree.NewTree;
import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.tree.RegExpLiteralTree;
import jdk.nashorn.api.tree.ReturnTree;
import jdk.nashorn.api.tree.SimpleTreeVisitorES5_1;
import jdk.nashorn.api.tree.SpreadTree;
import jdk.nashorn.api.tree.ThrowTree;
import jdk.nashorn.api.tree.Tree;
import jdk.nashorn.api.tree.TreeVisitor;
import jdk.nashorn.api.tree.TryTree;
import jdk.nashorn.api.tree.UnaryTree;
import jdk.nashorn.api.tree.WhileLoopTree;
import jdk.nashorn.api.tree.WithTree;
import jdk.nashorn.api.tree.YieldTree;
import org.apache.commons.lang3.StringUtils;

public class MatchExpressionValidator {

    private final Parser parser = Parser.create();

    private final TreeVisitor<Void, String> treeVisitor =
            new SimpleTreeVisitorES5_1<Void, String>() {
                Void fail(Tree node, String matchExpression) {
                    throw new IllegalMatchExpressionException(node, matchExpression);
                }

                @Override
                public Void visitBreak(BreakTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitCase(CaseTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitCatch(CatchTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitClassDeclaration(
                        ClassDeclarationTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitClassExpression(ClassExpressionTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitDoWhileLoop(DoWhileLoopTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitErroneous(ErroneousTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitExportEntry(ExportEntryTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitForInLoop(ForInLoopTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitForLoop(ForLoopTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitForOfLoop(ForOfLoopTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitFunctionCall(FunctionCallTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitFunctionDeclaration(
                        FunctionDeclarationTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitFunctionExpression(
                        FunctionExpressionTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitImportEntry(ImportEntryTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitInstanceOf(InstanceOfTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitLabeledStatement(
                        LabeledStatementTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitModule(ModuleTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitNew(NewTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitRegExpLiteral(RegExpLiteralTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitReturn(ReturnTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitSpread(SpreadTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitUnary(UnaryTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitUnknown(Tree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitWhileLoop(WhileLoopTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitWith(WithTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitYield(YieldTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitContinue(ContinueTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitDebugger(DebuggerTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitThrow(ThrowTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }

                @Override
                public Void visitTry(TryTree node, String matchExpression) {
                    return fail(node, matchExpression);
                }
            };

    String validate(Rule rule) throws IOException {
        try (StringReader reader = new StringReader(rule.getMatchExpression())) {
            CompilationUnitTree cut = parser.parse(rule.getName(), reader, null);
            if (cut == null) {
                throw new IllegalMatchExpressionException();
            }
            cut.accept(treeVisitor, rule.getMatchExpression());
        }
        return rule.getMatchExpression();
    }
}
