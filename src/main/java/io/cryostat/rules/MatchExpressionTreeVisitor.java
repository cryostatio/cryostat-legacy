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

import java.util.List;
import java.util.Set;

import org.openjdk.nashorn.api.tree.BreakTree;
import org.openjdk.nashorn.api.tree.CaseTree;
import org.openjdk.nashorn.api.tree.CatchTree;
import org.openjdk.nashorn.api.tree.ClassDeclarationTree;
import org.openjdk.nashorn.api.tree.ClassExpressionTree;
import org.openjdk.nashorn.api.tree.ContinueTree;
import org.openjdk.nashorn.api.tree.DebuggerTree;
import org.openjdk.nashorn.api.tree.DoWhileLoopTree;
import org.openjdk.nashorn.api.tree.ErroneousTree;
import org.openjdk.nashorn.api.tree.ExportEntryTree;
import org.openjdk.nashorn.api.tree.ExpressionTree;
import org.openjdk.nashorn.api.tree.ForInLoopTree;
import org.openjdk.nashorn.api.tree.ForLoopTree;
import org.openjdk.nashorn.api.tree.ForOfLoopTree;
import org.openjdk.nashorn.api.tree.FunctionCallTree;
import org.openjdk.nashorn.api.tree.FunctionDeclarationTree;
import org.openjdk.nashorn.api.tree.FunctionExpressionTree;
import org.openjdk.nashorn.api.tree.ImportEntryTree;
import org.openjdk.nashorn.api.tree.InstanceOfTree;
import org.openjdk.nashorn.api.tree.LabeledStatementTree;
import org.openjdk.nashorn.api.tree.MemberSelectTree;
import org.openjdk.nashorn.api.tree.ModuleTree;
import org.openjdk.nashorn.api.tree.NewTree;
import org.openjdk.nashorn.api.tree.RegExpLiteralTree;
import org.openjdk.nashorn.api.tree.ReturnTree;
import org.openjdk.nashorn.api.tree.SimpleTreeVisitorES5_1;
import org.openjdk.nashorn.api.tree.SpreadTree;
import org.openjdk.nashorn.api.tree.ThrowTree;
import org.openjdk.nashorn.api.tree.Tree;
import org.openjdk.nashorn.api.tree.TryTree;
import org.openjdk.nashorn.api.tree.UnaryTree;
import org.openjdk.nashorn.api.tree.WhileLoopTree;
import org.openjdk.nashorn.api.tree.WithTree;
import org.openjdk.nashorn.api.tree.YieldTree;

class MatchExpressionTreeVisitor extends SimpleTreeVisitorES5_1<Void, String> {

    static final Set<String> ALLOWED_FUNCTIONS = Set.of("test");

    private Void fail(Tree node, String matchExpression) {
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
    public Void visitClassDeclaration(ClassDeclarationTree node, String matchExpression) {
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
        ExpressionTree functionNode = node.getFunctionSelect();
        boolean isMemberSelectTree = functionNode instanceof MemberSelectTree;

        boolean isAcceptedFunction =
                isMemberSelectTree
                        && hasValidFunctionName(functionNode)
                        && isCalledOnRegExp(functionNode)
                        && containsValidArgument(node);

        if (isAcceptedFunction) {
            return super.visitFunctionCall(node, matchExpression);
        } else {
            return fail(node, matchExpression);
        }
    }

    private boolean hasValidFunctionName(ExpressionTree functionNode) {
        return ALLOWED_FUNCTIONS.contains(((MemberSelectTree) functionNode).getIdentifier());
    }

    private boolean isCalledOnRegExp(ExpressionTree functionNode) {
        return ((MemberSelectTree) functionNode).getExpression() instanceof RegExpLiteralTree;
    }

    // validate that exactly one argument was passed to the regexp.test() function, and that
    // argument must be either a string literal or a member access (ex. target.alias)
    private boolean containsValidArgument(FunctionCallTree node) {
        List<? extends ExpressionTree> arguments = node.getArguments();

        if (arguments.size() != 1) {
            return false;
        }

        switch (arguments.get(0).getKind()) {
            case MEMBER_SELECT:
                return true;
            case STRING_LITERAL:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Void visitFunctionDeclaration(FunctionDeclarationTree node, String matchExpression) {
        return fail(node, matchExpression);
    }

    @Override
    public Void visitFunctionExpression(FunctionExpressionTree node, String matchExpression) {
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
    public Void visitLabeledStatement(LabeledStatementTree node, String matchExpression) {
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
}
