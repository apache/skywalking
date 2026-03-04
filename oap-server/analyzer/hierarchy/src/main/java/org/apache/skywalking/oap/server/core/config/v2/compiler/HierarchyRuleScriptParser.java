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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleLexer;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleParser;
import org.apache.skywalking.hierarchy.rt.grammar.HierarchyRuleParserBaseVisitor;

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

    public static HierarchyRuleModel parse(final String expression) {
        final HierarchyRuleLexer lexer = new HierarchyRuleLexer(
            CharStreams.fromString(expression));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final HierarchyRuleParser parser = new HierarchyRuleParser(tokens);

        final List<String> errors = new ArrayList<>();
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer,
                                    final Object offendingSymbol,
                                    final int line,
                                    final int charPositionInLine,
                                    final String msg,
                                    final RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + " " + msg);
            }
        });

        final HierarchyRuleParser.MatchingRuleContext tree = parser.matchingRule();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "Hierarchy rule parsing failed: " + String.join("; ", errors)
                    + " in expression: " + expression);
        }

        return new HierarchyRuleModelVisitor().visit(tree);
    }

    /**
     * Visitor that transforms the ANTLR4 parse tree into {@link HierarchyRuleModel}.
     */
    private static final class HierarchyRuleModelVisitor
            extends HierarchyRuleParserBaseVisitor<HierarchyRuleModel> {

        @Override
        public HierarchyRuleModel visitMatchingRule(final HierarchyRuleParser.MatchingRuleContext ctx) {
            final String upperParam = ctx.param(0).getText();
            final String lowerParam = ctx.param(1).getText();
            final HierarchyRuleModel.RuleBody body = convertRuleBody(ctx.ruleBody());
            return HierarchyRuleModel.of(upperParam, lowerParam, body);
        }

        private HierarchyRuleModel.RuleBody convertRuleBody(
                final HierarchyRuleParser.RuleBodyContext ctx) {
            if (ctx.simpleExpression() != null) {
                return convertSimpleExpression(ctx.simpleExpression());
            }
            return convertBlockBody(ctx.blockBody());
        }

        private HierarchyRuleModel.SimpleComparison convertSimpleExpression(
                final HierarchyRuleParser.SimpleExpressionContext ctx) {
            final HierarchyRuleModel.Expr left = new ExprVisitor().visitRuleExpr(ctx.ruleExpr(0));
            final HierarchyRuleModel.Expr right = new ExprVisitor().visitRuleExpr(ctx.ruleExpr(1));
            final HierarchyRuleModel.CompareOp op = ctx.DEQ() != null
                ? HierarchyRuleModel.CompareOp.EQ : HierarchyRuleModel.CompareOp.NEQ;
            return new HierarchyRuleModel.SimpleComparison(left, op, right);
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
            return convertReturnStatement(ctx.returnStatement());
        }

        private HierarchyRuleModel.IfStatement convertIfStatement(
                final HierarchyRuleParser.IfStatementContext ctx) {
            final HierarchyRuleModel.Condition condition =
                new ConditionVisitor().visit(ctx.condition(0));

            final List<HierarchyRuleModel.Statement> thenBranch = new ArrayList<>();
            if (ctx.returnStatement(0) != null) {
                thenBranch.add(convertReturnStatement(ctx.returnStatement(0)));
            } else if (ctx.blockBody(0) != null) {
                thenBranch.addAll(convertBlockBody(ctx.blockBody(0)).getStatements());
            }

            final List<HierarchyRuleModel.Statement> elseBranch = new ArrayList<>();
            // Handle else-if and else branches
            final int condCount = ctx.condition().size();
            final int retCount = ctx.returnStatement().size();
            final int blockCount = ctx.blockBody().size();

            // If there are more conditions (else if branches)
            if (condCount > 1 || retCount > 1 || blockCount > 1) {
                // Simplification: flatten else-if into the else branch
                // For the current hierarchy rules, we don't have else-if patterns
                // so this handles the basic else case
                if (retCount > 1) {
                    elseBranch.add(convertReturnStatement(ctx.returnStatement(retCount - 1)));
                } else if (blockCount > 1) {
                    elseBranch.addAll(
                        convertBlockBody(ctx.blockBody(blockCount - 1)).getStatements());
                }
            }

            return new HierarchyRuleModel.IfStatement(condition, thenBranch, elseBranch);
        }

        private HierarchyRuleModel.ReturnStatement convertReturnStatement(
                final HierarchyRuleParser.ReturnStatementContext ctx) {
            final HierarchyRuleParser.ReturnValueContext rv = ctx.returnValue();
            if (rv instanceof HierarchyRuleParser.ReturnComparisonContext) {
                final HierarchyRuleParser.ReturnComparisonContext rc =
                    (HierarchyRuleParser.ReturnComparisonContext) rv;
                final ExprVisitor ev = new ExprVisitor();
                final HierarchyRuleModel.SimpleComparison comp =
                    new HierarchyRuleModel.SimpleComparison(
                        ev.visitRuleExpr(rc.ruleExpr(0)),
                        HierarchyRuleModel.CompareOp.EQ,
                        ev.visitRuleExpr(rc.ruleExpr(1)));
                return new HierarchyRuleModel.ReturnStatement(comp);
            }
            if (rv instanceof HierarchyRuleParser.ReturnNeqComparisonContext) {
                final HierarchyRuleParser.ReturnNeqComparisonContext rnc =
                    (HierarchyRuleParser.ReturnNeqComparisonContext) rv;
                final ExprVisitor ev = new ExprVisitor();
                final HierarchyRuleModel.SimpleComparison comp =
                    new HierarchyRuleModel.SimpleComparison(
                        ev.visitRuleExpr(rnc.ruleExpr(0)),
                        HierarchyRuleModel.CompareOp.NEQ,
                        ev.visitRuleExpr(rnc.ruleExpr(1)));
                return new HierarchyRuleModel.ReturnStatement(comp);
            }
            // returnExpr
            final HierarchyRuleParser.ReturnExprContext re =
                (HierarchyRuleParser.ReturnExprContext) rv;
            final HierarchyRuleModel.Expr value = new ExprVisitor().visitRuleExpr(re.ruleExpr());
            return new HierarchyRuleModel.ReturnStatement(value);
        }
    }

    /**
     * Visitor for condition nodes.
     */
    private static final class ConditionVisitor
            extends HierarchyRuleParserBaseVisitor<HierarchyRuleModel.Condition> {

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
            return new HierarchyRuleModel.NotCondition(visit(ctx.condition()));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondParen(
                final HierarchyRuleParser.CondParenContext ctx) {
            return visit(ctx.condition());
        }

        @Override
        public HierarchyRuleModel.Condition visitCondEq(
                final HierarchyRuleParser.CondEqContext ctx) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ComparisonCondition(
                ev.visitRuleExpr(ctx.ruleExpr(0)),
                HierarchyRuleModel.CompareOp.EQ,
                ev.visitRuleExpr(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondNeq(
                final HierarchyRuleParser.CondNeqContext ctx) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ComparisonCondition(
                ev.visitRuleExpr(ctx.ruleExpr(0)),
                HierarchyRuleModel.CompareOp.NEQ,
                ev.visitRuleExpr(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondGt(
                final HierarchyRuleParser.CondGtContext ctx) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ComparisonCondition(
                ev.visitRuleExpr(ctx.ruleExpr(0)),
                HierarchyRuleModel.CompareOp.GT,
                ev.visitRuleExpr(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondLt(
                final HierarchyRuleParser.CondLtContext ctx) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ComparisonCondition(
                ev.visitRuleExpr(ctx.ruleExpr(0)),
                HierarchyRuleModel.CompareOp.LT,
                ev.visitRuleExpr(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Condition visitCondExpr(
                final HierarchyRuleParser.CondExprContext ctx) {
            final ExprVisitor ev = new ExprVisitor();
            return new HierarchyRuleModel.ExprCondition(ev.visitRuleExpr(ctx.ruleExpr()));
        }
    }

    /**
     * Visitor for expression nodes.
     */
    private static final class ExprVisitor
            extends HierarchyRuleParserBaseVisitor<HierarchyRuleModel.Expr> {

        public HierarchyRuleModel.Expr visitRuleExpr(
                final HierarchyRuleParser.RuleExprContext ctx) {
            return visit(ctx);
        }

        @Override
        public HierarchyRuleModel.Expr visitExprAdd(
                final HierarchyRuleParser.ExprAddContext ctx) {
            return new HierarchyRuleModel.BinaryExpr(
                visit(ctx.ruleExpr(0)),
                HierarchyRuleModel.ArithmeticOp.ADD,
                visit(ctx.ruleExpr(1)));
        }

        @Override
        public HierarchyRuleModel.Expr visitExprSub(
                final HierarchyRuleParser.ExprSubContext ctx) {
            return new HierarchyRuleModel.BinaryExpr(
                visit(ctx.ruleExpr(0)),
                HierarchyRuleModel.ArithmeticOp.SUB,
                visit(ctx.ruleExpr(1)));
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
            return new HierarchyRuleModel.StringLiteralExpr(stripQuotes(ctx.STRING().getText()));
        }

        @Override
        public HierarchyRuleModel.Expr visitExprNumber(
                final HierarchyRuleParser.ExprNumberContext ctx) {
            return new HierarchyRuleModel.NumberLiteralExpr(Long.parseLong(ctx.NUMBER().getText()));
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
            final HierarchyRuleParser.ChainFieldAccessContext fa =
                (HierarchyRuleParser.ChainFieldAccessContext) ctx;
            return new HierarchyRuleModel.FieldAccess(fa.IDENTIFIER().getText());
        }
    }

    private static String stripQuotes(final String s) {
        if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
