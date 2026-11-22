/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.RuleNode;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleLexer;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleParser;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleParserBaseVisitor;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

/**
 * Facade: parses hierarchy rule expression strings into {@link HierarchyRuleModel}.
 *
 * <pre>
 *   HierarchyRuleModel model = HierarchyRuleScriptParser.parse(
 *       "{ (u, l) -&gt; u.name == l.name }");
 * </pre>
 */
public final class HierarchyRuleScriptParser {

    private HierarchyRuleScriptParser() {
    }

    /**
     * @throws IllegalArgumentException for what the expression does not support, without repeating the expression:
     *                                  {@link CompiledHierarchyRuleProvider} names the rule, its line and the
     *                                  expression
     */
    public static HierarchyRuleModel parse(final String expression) {
        final HierarchyRuleLexer lexer = new HierarchyRuleLexer(
            CharStreams.fromString(expression));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final HierarchyRuleParser parser = new HierarchyRuleParser(tokens);

        final List<String> errors = new ArrayList<>();
        // The lexer reports too: by default it prints an unrecognized character and drops it.
        final BaseErrorListener listener = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer,
                                    final Object offendingSymbol,
                                    final int line,
                                    final int charPositionInLine,
                                    final String msg,
                                    final RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + " " + msg);
            }
        };
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        final HierarchyRuleParser.MatchingRuleContext tree = parser.matchingRule();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Hierarchy rule parsing failed: " + String.join("; ", errors));
        }

        return new HierarchyRuleModelVisitor().visit(tree);
    }

    /**
     * Fails on any parse-tree alternative a subclass has no visit method for, so a grammar
     * alternative added without a model for it cannot silently turn into {@code null}.
     */
    private abstract static class StrictVisitor<T> extends HierarchyRuleParserBaseVisitor<T> {
        @Override
        public T visitChildren(final RuleNode node) {
            throw new IllegalArgumentException(
                "Unsupported hierarchy rule construct " + node.getClass().getSimpleName()
                    + ": '" + node.getText() + "'");
        }
    }

    /**
     * Visitor that transforms the ANTLR4 parse tree into {@link HierarchyRuleModel}.
     */
    private static final class HierarchyRuleModelVisitor extends StrictVisitor<HierarchyRuleModel> {

        @Override
        public HierarchyRuleModel visitMatchingRule(final HierarchyRuleParser.MatchingRuleContext ctx) {
            final String upperParam = ctx.param(0).getText();
            final String lowerParam = ctx.param(1).getText();
            if (upperParam.equals(lowerParam)) {
                throw new IllegalArgumentException(
                    "Hierarchy rule parameters must have distinct names, got (" + upperParam + ", "
                        + lowerParam + ")");
            }
            final HierarchyRuleModel.RuleBody body = convertRuleBody(ctx.ruleBody());
            return HierarchyRuleModel.of(upperParam, lowerParam, body);
        }

        private HierarchyRuleModel.RuleBody convertRuleBody(
                final HierarchyRuleParser.RuleBodyContext ctx) {
            if (ctx.simpleExpression() != null) {
                return convertSimpleExpression(ctx.simpleExpression());
            }
            if (ctx.blockBody() != null) {
                final HierarchyRuleModel.BlockBody block = convertBlockBody(ctx.blockBody());
                if (!alwaysReturns(block.getStatements())) {
                    // Groovy would return null there, which the v1 caller could not unbox.
                    throw new IllegalArgumentException(
                        "Hierarchy rule block can finish without a return statement");
                }
                return block;
            }
            throw unsupported("rule body", ctx);
        }

        private HierarchyRuleModel.SimpleComparison convertSimpleExpression(
                final HierarchyRuleParser.SimpleExpressionContext ctx) {
            final HierarchyRuleModel.CompareOp op;
            if (ctx.DEQ() != null) {
                op = HierarchyRuleModel.CompareOp.EQ;
            } else if (ctx.NEQ() != null) {
                op = HierarchyRuleModel.CompareOp.NEQ;
            } else {
                throw unsupported("comparison", ctx);
            }
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.SimpleComparison(
                ev.visit(ctx.ruleExpr(0)), op, ev.visit(ctx.ruleExpr(1)));
        }

        private HierarchyRuleModel.BlockBody convertBlockBody(
                final HierarchyRuleParser.BlockBodyContext ctx) {
            final List<HierarchyRuleModel.Statement> stmts = new ArrayList<>();
            for (final HierarchyRuleParser.StatementContext stmtCtx : ctx.statement()) {
                stmts.add(convertStatement(stmtCtx));
            }
            return new HierarchyRuleModel.BlockBody(stmts);
        }

        private HierarchyRuleModel.Statement convertStatement(
                final HierarchyRuleParser.StatementContext ctx) {
            if (ctx.ifStatement() != null) {
                return convertIfStatement(ctx.ifStatement());
            }
            if (ctx.returnStatement() != null) {
                return convertReturnStatement(ctx.returnStatement());
            }
            throw unsupported("statement", ctx);
        }

        /**
         * {@code if (a) x else if (b) y else z} becomes {@code if (a) x else { if (b) y else z }},
         * built from the last condition outwards.
         */
        private HierarchyRuleModel.IfStatement convertIfStatement(
                final HierarchyRuleParser.IfStatementContext ctx) {
            final List<HierarchyRuleParser.ConditionContext> conditions = ctx.condition();
            final List<HierarchyRuleParser.IfBranchContext> branches = ctx.ifBranch();
            final boolean hasElse = branches.size() == conditions.size() + 1;
            if (!hasElse && branches.size() != conditions.size()) {
                throw unsupported("if statement", ctx);
            }
            List<HierarchyRuleModel.Statement> elseBranch = hasElse
                ? convertIfBranch(branches.get(branches.size() - 1))
                : new ArrayList<>();
            HierarchyRuleModel.IfStatement ifStmt = null;
            for (int i = conditions.size() - 1; i >= 0; i--) {
                ifStmt = new HierarchyRuleModel.IfStatement(
                    new ConditionVisitor().visit(conditions.get(i)),
                    convertIfBranch(branches.get(i)),
                    elseBranch);
                elseBranch = new ArrayList<>();
                elseBranch.add(ifStmt);
            }
            return ifStmt;
        }

        private List<HierarchyRuleModel.Statement> convertIfBranch(
                final HierarchyRuleParser.IfBranchContext ctx) {
            if (ctx.returnStatement() != null) {
                final List<HierarchyRuleModel.Statement> stmts = new ArrayList<>();
                stmts.add(convertReturnStatement(ctx.returnStatement()));
                return stmts;
            }
            if (ctx.blockBody() != null) {
                return new ArrayList<>(convertBlockBody(ctx.blockBody()).getStatements());
            }
            throw unsupported("if branch", ctx);
        }

        private HierarchyRuleModel.ReturnStatement convertReturnStatement(
                final HierarchyRuleParser.ReturnStatementContext ctx) {
            final HierarchyRuleParser.ReturnValueContext rv = ctx.returnValue();
            if (rv.getStart().getLine() != ctx.RETURN().getSymbol().getLine()) {
                // Groovy ends a bare return at the line break: the value on the next line is never returned.
                throw new IllegalArgumentException("A return whose value starts on the next line is ambiguous: "
                    + "put the value on the return's line");
            }
            final ExprVisitor ev = new ExprVisitor();
            if (rv instanceof HierarchyRuleParser.ReturnComparisonContext) {
                final HierarchyRuleParser.ReturnComparisonContext rc =
                    (HierarchyRuleParser.ReturnComparisonContext) rv;
                return new HierarchyRuleModel.ReturnStatement(
                    new HierarchyRuleModel.SimpleComparison(
                        ev.visit(rc.ruleExpr(0)),
                        HierarchyRuleModel.CompareOp.EQ,
                        ev.visit(rc.ruleExpr(1))));
            }
            if (rv instanceof HierarchyRuleParser.ReturnNeqComparisonContext) {
                final HierarchyRuleParser.ReturnNeqComparisonContext rnc =
                    (HierarchyRuleParser.ReturnNeqComparisonContext) rv;
                return new HierarchyRuleModel.ReturnStatement(
                    new HierarchyRuleModel.SimpleComparison(
                        ev.visit(rnc.ruleExpr(0)),
                        HierarchyRuleModel.CompareOp.NEQ,
                        ev.visit(rnc.ruleExpr(1))));
            }
            if (rv instanceof HierarchyRuleParser.ReturnExprContext) {
                return new HierarchyRuleModel.ReturnStatement(
                    ev.visit(((HierarchyRuleParser.ReturnExprContext) rv).ruleExpr()));
            }
            throw unsupported("return value", rv);
        }

        private static boolean alwaysReturns(final List<HierarchyRuleModel.Statement> stmts) {
            for (final HierarchyRuleModel.Statement stmt : stmts) {
                if (alwaysReturns(stmt)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean alwaysReturns(final HierarchyRuleModel.Statement stmt) {
            if (stmt instanceof HierarchyRuleModel.ReturnStatement) {
                return true;
            }
            if (stmt instanceof HierarchyRuleModel.IfStatement) {
                final HierarchyRuleModel.IfStatement ifStmt = (HierarchyRuleModel.IfStatement) stmt;
                return alwaysReturns(ifStmt.getThenBranch()) && alwaysReturns(ifStmt.getElseBranch());
            }
            throw new IllegalArgumentException(
                "Unsupported hierarchy rule statement: " + stmt.getClass().getSimpleName());
        }
    }

    /**
     * Visitor for condition nodes.
     */
    private static final class ConditionVisitor extends StrictVisitor<HierarchyRuleModel.Condition> {

        @Override
        public HierarchyRuleModel.Condition visitCondAnd(
                final HierarchyRuleParser.CondAndContext ctx) {
            return new HierarchyRuleModel.LogicalCondition(
                visit(ctx.condition(0)),
                HierarchyRuleModel.LogicalOp.AND,
                visit(ctx.condition(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondOr(
                final HierarchyRuleParser.CondOrContext ctx) {
            return new HierarchyRuleModel.LogicalCondition(
                visit(ctx.condition(0)),
                HierarchyRuleModel.LogicalOp.OR,
                visit(ctx.condition(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondNot(
                final HierarchyRuleParser.CondNotContext ctx) {
            final HierarchyRuleParser.ConditionContext inner = ctx.condition();
            if (inner instanceof HierarchyRuleParser.CondEqContext
                    || inner instanceof HierarchyRuleParser.CondNeqContext
                    || inner instanceof HierarchyRuleParser.CondGtContext
                    || inner instanceof HierarchyRuleParser.CondLtContext
                    || inner instanceof HierarchyRuleParser.CondGteContext
                    || inner instanceof HierarchyRuleParser.CondLteContext
                    || inner instanceof HierarchyRuleParser.CondExprContext
                    && ((HierarchyRuleParser.CondExprContext) inner).ruleExpr()
                        instanceof HierarchyRuleParser.ExprAddSubContext) {
                // The grammar reads `!a == b` as `!(a == b)` and `!a + b` as `!(a + b)`; Groovy applies `!` to `a`.
                throw new IllegalArgumentException(
                    "Ambiguous negation '" + ctx.getText() + "': Groovy applies '!' to the left"
                        + " operand only; write '!(a == b)' to negate the comparison");
            }
            return new HierarchyRuleModel.NotCondition(visit(inner));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondParen(
                final HierarchyRuleParser.CondParenContext ctx) {
            return visit(ctx.condition());
        }

        @Override
        public HierarchyRuleModel.Condition visitCondEq(
                final HierarchyRuleParser.CondEqContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.EQ, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondNeq(
                final HierarchyRuleParser.CondNeqContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.NEQ, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondGt(
                final HierarchyRuleParser.CondGtContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.GT, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondLt(
                final HierarchyRuleParser.CondLtContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.LT, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondGte(
                final HierarchyRuleParser.CondGteContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.GTE, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondLte(
                final HierarchyRuleParser.CondLteContext ctx) {
            return comparison(ctx.ruleExpr(0), HierarchyRuleModel.CompareOp.LTE, ctx.ruleExpr(1));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondExpr(
                final HierarchyRuleParser.CondExprContext ctx) {
            return new HierarchyRuleModel.ExprCondition(new ExprVisitor().visit(ctx.ruleExpr()));
        }

        private static HierarchyRuleModel.Condition comparison(
                final HierarchyRuleParser.RuleExprContext left,
                final HierarchyRuleModel.CompareOp op,
                final HierarchyRuleParser.RuleExprContext right) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ComparisonCondition(ev.visit(left), op, ev.visit(right));
        }
    }

    /**
     * Visitor for expression nodes.
     */
    private static final class ExprVisitor extends StrictVisitor<HierarchyRuleModel.Expr> {

        @Override
        public HierarchyRuleModel.Expr visitExprAddSub(
                final HierarchyRuleParser.ExprAddSubContext ctx) {
            final HierarchyRuleModel.ArithmeticOp op;
            if (ctx.op.getType() == HierarchyRuleParser.PLUS) {
                op = HierarchyRuleModel.ArithmeticOp.ADD;
            } else if (ctx.op.getType() == HierarchyRuleParser.MINUS) {
                op = HierarchyRuleModel.ArithmeticOp.SUB;
            } else {
                throw unsupported("arithmetic operator", ctx);
            }
            return new HierarchyRuleModel.BinaryExpr(visit(ctx.ruleExpr(0)), op, visit(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Expr visitExprPrimary(
                final HierarchyRuleParser.ExprPrimaryContext ctx) {
            return visit(ctx.ruleExprPrimary());
        }

        @Override
        public HierarchyRuleModel.Expr visitExprMethodChain(
                final HierarchyRuleParser.ExprMethodChainContext ctx) {
            return convertMethodChain(ctx.methodChain());
        }

        @Override
        public HierarchyRuleModel.Expr visitExprString(
                final HierarchyRuleParser.ExprStringContext ctx) {
            return new HierarchyRuleModel.StringLiteralExpr(unquote(ctx.STRING().getText()));
        }

        @Override
        public HierarchyRuleModel.Expr visitExprNumber(
                final HierarchyRuleParser.ExprNumberContext ctx) {
            final String text = ctx.NUMBER().getText();
            final BigInteger value = DslNumbers.wholeLiteral(text, "The literal " + text);
            if (value.bitLength() > 63) {
                throw new IllegalArgumentException("The literal " + text + " is beyond a long");
            }
            return new HierarchyRuleModel.NumberLiteralExpr(value.longValue());
        }

        @Override
        public HierarchyRuleModel.Expr visitExprTrue(
                final HierarchyRuleParser.ExprTrueContext ctx) {
            return new HierarchyRuleModel.BoolLiteralExpr(true);
        }

        @Override
        public HierarchyRuleModel.Expr visitExprFalse(
                final HierarchyRuleParser.ExprFalseContext ctx) {
            return new HierarchyRuleModel.BoolLiteralExpr(false);
        }

        private HierarchyRuleModel.MethodChainExpr convertMethodChain(
                final HierarchyRuleParser.MethodChainContext ctx) {
            final String target = ctx.IDENTIFIER().getText();
            final List<HierarchyRuleModel.ChainSegment> segments = new ArrayList<>();
            for (final HierarchyRuleParser.ChainSegmentContext seg : ctx.chainSegment()) {
                segments.add(convertChainSegment(seg));
            }
            return new HierarchyRuleModel.MethodChainExpr(target, segments);
        }

        private HierarchyRuleModel.ChainSegment convertChainSegment(
                final HierarchyRuleParser.ChainSegmentContext ctx) {
            if (ctx instanceof HierarchyRuleParser.ChainMethodCallContext) {
                final HierarchyRuleParser.ChainMethodCallContext mc =
                    (HierarchyRuleParser.ChainMethodCallContext) ctx;
                final String name = mc.IDENTIFIER().getText();
                final List<HierarchyRuleModel.Expr> args = new ArrayList<>();
                if (mc.argList() != null) {
                    for (final HierarchyRuleParser.RuleExprContext argCtx :
                            mc.argList().ruleExpr()) {
                        args.add(visit(argCtx));
                    }
                }
                return new HierarchyRuleModel.MethodCallSegment(name, args);
            }
            if (ctx instanceof HierarchyRuleParser.ChainFieldAccessContext) {
                return new HierarchyRuleModel.FieldAccess(
                    ((HierarchyRuleParser.ChainFieldAccessContext) ctx).IDENTIFIER().getText());
            }
            throw unsupported("chain segment", ctx);
        }
    }

    /**
     * Strips the quotes of a {@code STRING} token, refusing what Groovy would interpolate in a double-quoted one.
     */
    private static String unquote(final String token) {
        final String body = token.substring(1, token.length() - 1);
        if (token.charAt(0) == '"' && DslStrings.firstInterpolation(body) >= 0) {
            throw new IllegalArgumentException(
                "GString interpolation is not supported: " + token
                    + "; use a single-quoted string, or concat() / + to build the value");
        }
        // the text between the quotes as written, as hierarchy rules have always read it: a regexp's \d or a path's
        // C:\temp keeps its backslash
        return body;
    }

    private static IllegalArgumentException unsupported(final String what,
                                                        final ParserRuleContext ctx) {
        return new IllegalArgumentException(
            "Unsupported hierarchy rule " + what + ": '" + ctx.getText() + "'");
    }
}
