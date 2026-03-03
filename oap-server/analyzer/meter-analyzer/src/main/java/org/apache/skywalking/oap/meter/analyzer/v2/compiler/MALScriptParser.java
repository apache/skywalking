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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.apache.skywalking.mal.rt.grammar.MALLexer;
import org.apache.skywalking.mal.rt.grammar.MALParser;
import org.apache.skywalking.mal.rt.grammar.MALParserBaseVisitor;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.Argument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ArithmeticOp;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.BinaryExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.BoolArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureAssignment;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureBinaryExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureBoolLiteral;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureChainSegment;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureCondition;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureExprCondition;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureExprStatement;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureFieldAccess;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureIfStatement;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureIndexAccess;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureMethodCallSeg;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureMethodChain;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureNullLiteral;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureNumberLiteral;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureReturnStatement;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureStatement;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureStringLiteral;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureVarAssign;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureVarDecl;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.CompareOp;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.EnumRefArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.Expr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ExprArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.FunctionCallExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.LogicalOp;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.MetricExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.MethodCall;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.NumberExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.NumberListArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ParenChainExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.StringArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.StringListArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.UnaryNegExpr;

/**
 * Facade: parses MAL expression strings into {@link MALExpressionModel.Expr} AST.
 *
 * <pre>
 *   MALExpressionModel.Expr ast = MALScriptParser.parse(
 *       "metric.sum(['tag']).rate('PT1M').service(['svc'], Layer.GENERAL)");
 * </pre>
 */
public final class MALScriptParser {

    private MALScriptParser() {
    }

    /**
     * Pre-process expression to convert Groovy regex literals used as method
     * arguments into string literals. E.g., {@code split(/\|/, -1)} becomes
     * {@code split("\\|", -1)}. Regex literals after {@code =~} are handled
     * by the lexer mode and are NOT touched here.
     */
    static String preprocessRegexLiterals(final String expression) {
        // Match /pattern/ that appears after ( or , (method arg context),
        // but NOT after =~ (which is handled by lexer mode)
        final Pattern argRegex = Pattern.compile(
            "(?<=[,(])\\s*/([^/\\r\\n]+)/");
        final Matcher m = argRegex.matcher(expression);
        if (!m.find()) {
            return expression;
        }
        final StringBuffer sb = new StringBuffer();
        m.reset();
        while (m.find()) {
            // Escape backslashes: regex literal \| must become string literal \\|
            // because the MAL lexer only recognizes \\, \", \' etc. as escape sequences
            final String body = m.group(1).replace("\\", "\\\\");
            // Preserve leading whitespace from the match
            final String leading = m.group().substring(0, m.group().indexOf('/'));
            m.appendReplacement(sb,
                java.util.regex.Matcher.quoteReplacement(
                    leading + "\"" + body + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Expr parse(final String expression) {
        final String preprocessed = preprocessRegexLiterals(expression);
        final MALLexer lexer = new MALLexer(CharStreams.fromString(preprocessed));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final MALParser parser = new MALParser(tokens);

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

        final MALParser.ExpressionContext tree = parser.expression();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "MAL expression parsing failed: " + String.join("; ", errors)
                    + " in expression: " + expression);
        }

        return new MALExprVisitor().visit(tree.additiveExpression());
    }

    /**
     * Parse a standalone filter closure expression into a {@link ClosureArgument}.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     */
    public static ClosureArgument parseFilter(final String filterExpression) {
        final MALLexer lexer = new MALLexer(CharStreams.fromString(filterExpression));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final MALParser parser = new MALParser(tokens);

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

        final MALParser.FilterExpressionContext tree = parser.filterExpression();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "MAL filter expression parsing failed: " + String.join("; ", errors)
                    + " in expression: " + filterExpression);
        }

        return new ClosureVisitor().visitClosure(tree.closureExpression());
    }

    /**
     * Visitor transforming ANTLR4 parse tree into MAL expression AST.
     */
    private static final class MALExprVisitor extends MALParserBaseVisitor<Expr> {

        @Override
        public Expr visitAdditiveExpression(final MALParser.AdditiveExpressionContext ctx) {
            Expr result = visit(ctx.multiplicativeExpression(0));
            for (int i = 1; i < ctx.multiplicativeExpression().size(); i++) {
                final ArithmeticOp op = ctx.getChild(2 * i - 1).getText().equals("+")
                    ? ArithmeticOp.ADD : ArithmeticOp.SUB;
                result = new BinaryExpr(result, op, visit(ctx.multiplicativeExpression(i)));
            }
            return result;
        }

        @Override
        public Expr visitMultiplicativeExpression(
                final MALParser.MultiplicativeExpressionContext ctx) {
            Expr result = visit(ctx.unaryExpression(0));
            for (int i = 1; i < ctx.unaryExpression().size(); i++) {
                final ArithmeticOp op = ctx.getChild(2 * i - 1).getText().equals("*")
                    ? ArithmeticOp.MUL : ArithmeticOp.DIV;
                result = new BinaryExpr(result, op, visit(ctx.unaryExpression(i)));
            }
            return result;
        }

        @Override
        public Expr visitUnaryNeg(final MALParser.UnaryNegContext ctx) {
            return new UnaryNegExpr(visit(ctx.unaryExpression()));
        }

        @Override
        public Expr visitUnaryPostfix(final MALParser.UnaryPostfixContext ctx) {
            return visit(ctx.postfixExpression());
        }

        @Override
        public Expr visitUnaryNumber(final MALParser.UnaryNumberContext ctx) {
            return new NumberExpr(Double.parseDouble(ctx.NUMBER().getText()));
        }

        @Override
        public Expr visitPostfixExpression(final MALParser.PostfixExpressionContext ctx) {
            final List<MethodCall> methods = new ArrayList<>();
            for (final MALParser.MethodCallContext mc : ctx.methodCall()) {
                methods.add(visitMethodCallNode(mc));
            }

            final MALParser.PrimaryContext primary = ctx.primary();
            if (primary.functionCall() != null) {
                final MALParser.FunctionCallContext fc = primary.functionCall();
                final List<Argument> args = fc.argumentList() != null
                    ? visitArgList(fc.argumentList()) : Collections.emptyList();
                return new FunctionCallExpr(fc.IDENTIFIER().getText(), args, methods);
            }

            if (primary.additiveExpression() != null) {
                final Expr inner = visit(primary.additiveExpression());
                if (methods.isEmpty()) {
                    return inner;
                }
                return new ParenChainExpr(inner, methods);
            }

            return new MetricExpr(primary.IDENTIFIER().getText(), methods);
        }

        private MethodCall visitMethodCallNode(final MALParser.MethodCallContext ctx) {
            final String name = ctx.IDENTIFIER().getText();
            final List<Argument> args = ctx.argumentList() != null
                ? visitArgList(ctx.argumentList()) : Collections.emptyList();
            return new MethodCall(name, args);
        }

        private List<Argument> visitArgList(final MALParser.ArgumentListContext ctx) {
            final List<Argument> args = new ArrayList<>();
            for (final MALParser.ArgumentContext argCtx : ctx.argument()) {
                args.add(convertArgument(argCtx));
            }
            return args;
        }

        private Argument convertArgument(final MALParser.ArgumentContext ctx) {
            if (ctx.stringList() != null) {
                return convertStringList(ctx.stringList());
            }
            if (ctx.numberList() != null) {
                return convertNumberList(ctx.numberList());
            }
            if (ctx.closureExpression() != null) {
                return new ClosureVisitor().visitClosure(ctx.closureExpression());
            }
            if (ctx.enumRef() != null) {
                return new EnumRefArgument(
                    ctx.enumRef().IDENTIFIER(0).getText(),
                    ctx.enumRef().IDENTIFIER(1).getText());
            }
            if (ctx.STRING() != null) {
                return new StringArgument(stripQuotes(ctx.STRING().getText()));
            }
            if (ctx.boolLiteral() != null) {
                return new BoolArgument(ctx.boolLiteral().TRUE() != null);
            }
            if (ctx.NULL() != null) {
                return new MALExpressionModel.NullArgument();
            }
            // additiveExpression — nested expression
            return new ExprArgument(visit(ctx.additiveExpression()));
        }

        private StringListArgument convertStringList(final MALParser.StringListContext ctx) {
            final List<String> values = new ArrayList<>();
            ctx.STRING().forEach(s -> values.add(stripQuotes(s.getText())));
            return new StringListArgument(values);
        }

        private NumberListArgument convertNumberList(final MALParser.NumberListContext ctx) {
            final List<Double> values = new ArrayList<>();
            ctx.NUMBER().forEach(n -> values.add(Double.parseDouble(n.getText())));
            return new NumberListArgument(values);
        }
    }

    /**
     * Visitor for closure expressions within MAL.
     */
    private static final class ClosureVisitor {

        ClosureArgument visitClosure(final MALParser.ClosureExpressionContext ctx) {
            final List<String> params = new ArrayList<>();
            if (ctx.closureParams() != null) {
                ctx.closureParams().IDENTIFIER().forEach(id -> params.add(id.getText()));
            }
            final List<ClosureStatement> body = convertClosureBody(ctx.closureBody());
            return new ClosureArgument(params, body);
        }

        private List<ClosureStatement> convertClosureBody(
                final MALParser.ClosureBodyContext ctx) {
            // Bare condition or braced condition: { tags -> tags.x == 'v' }
            if (ctx.closureCondition() != null) {
                final ClosureCondition cond = convertCondition(ctx.closureCondition());
                return List.of(new ClosureExprStatement(cond));
            }
            final List<ClosureStatement> stmts = new ArrayList<>();
            for (final MALParser.ClosureStatementContext stmtCtx : ctx.closureStatement()) {
                stmts.add(convertClosureStatement(stmtCtx));
            }
            return stmts;
        }

        private ClosureStatement convertClosureStatement(
                final MALParser.ClosureStatementContext ctx) {
            if (ctx.ifStatement() != null) {
                return convertIfStatement(ctx.ifStatement());
            }
            if (ctx.returnStatement() != null) {
                final ClosureExpr value = ctx.returnStatement().closureExpr() != null
                    ? convertClosureExpr(ctx.returnStatement().closureExpr()) : null;
                return new ClosureReturnStatement(value);
            }
            if (ctx.variableDeclaration() != null) {
                final MALParser.VariableDeclarationContext vd = ctx.variableDeclaration();
                final String typeName;
                final String varName;
                if (vd.DEF() != null) {
                    // def keyword: def matcher = ...
                    // Infer type from initializer
                    varName = vd.IDENTIFIER(0).getText();
                    final ClosureExpr init = convertClosureExpr(vd.closureExpr());
                    typeName = inferDefType(init);
                    return new ClosureVarDecl(typeName, varName, init);
                }
                if (vd.L_BRACKET() != null) {
                    // Array type: String[] parts = ...
                    typeName = vd.IDENTIFIER(0).getText() + "[]";
                } else {
                    typeName = vd.IDENTIFIER(0).getText();
                }
                return new ClosureVarDecl(
                    typeName,
                    vd.IDENTIFIER(1).getText(),
                    convertClosureExpr(vd.closureExpr()));
            }
            if (ctx.assignmentStatement() != null) {
                final MALParser.ClosureFieldAccessContext fa =
                    ctx.assignmentStatement().closureFieldAccess();
                final List<org.antlr.v4.runtime.tree.TerminalNode> ids = fa.IDENTIFIER();
                final String firstId = ids.get(0).getText();
                if (ids.size() == 1 && fa.closureExpr() == null) {
                    // bare variable assignment: result = '129'
                    final ClosureExpr value =
                        convertClosureExpr(ctx.assignmentStatement().closureExpr());
                    return new ClosureVarAssign(firstId, value);
                }
                // Map assignment: tags.field = value or tags[expr] = value
                final ClosureExpr keyExpr;
                if (fa.closureExpr() != null) {
                    // tags[expr] = value
                    keyExpr = convertClosureExpr(fa.closureExpr());
                } else {
                    // tags.field = value — the key is the last IDENTIFIER
                    keyExpr = new ClosureStringLiteral(ids.get(ids.size() - 1).getText());
                }
                final ClosureExpr value =
                    convertClosureExpr(ctx.assignmentStatement().closureExpr());
                return new ClosureAssignment(firstId, keyExpr, value);
            }
            // expressionStatement
            return new ClosureExprStatement(
                convertClosureExpr(ctx.expressionStatement().closureExpr()));
        }

        private ClosureIfStatement convertIfStatement(
                final MALParser.IfStatementContext ctx) {
            final ClosureCondition condition = convertCondition(ctx.closureCondition());
            final List<ClosureStatement> thenBranch = new ArrayList<>();
            if (ctx.closureBlock(0) != null) {
                for (final MALParser.ClosureStatementContext stmtCtx :
                        ctx.closureBlock(0).closureStatement()) {
                    thenBranch.add(convertClosureStatement(stmtCtx));
                }
            }
            List<ClosureStatement> elseBranch = null;
            // Check for else-if or else
            if (ctx.ifStatement() != null) {
                elseBranch = new ArrayList<>();
                elseBranch.add(convertIfStatement(ctx.ifStatement()));
            } else if (ctx.closureBlock().size() > 1) {
                elseBranch = new ArrayList<>();
                for (final MALParser.ClosureStatementContext stmtCtx :
                        ctx.closureBlock(1).closureStatement()) {
                    elseBranch.add(convertClosureStatement(stmtCtx));
                }
            }
            return new ClosureIfStatement(condition, thenBranch, elseBranch);
        }

        private ClosureCondition convertCondition(
                final MALParser.ClosureConditionContext ctx) {
            return convertConditionOr(ctx.closureConditionOr());
        }

        private ClosureCondition convertConditionOr(
                final MALParser.ClosureConditionOrContext ctx) {
            ClosureCondition result = convertConditionAnd(ctx.closureConditionAnd(0));
            for (int i = 1; i < ctx.closureConditionAnd().size(); i++) {
                result = new MALExpressionModel.ClosureLogical(
                    result, LogicalOp.OR, convertConditionAnd(ctx.closureConditionAnd(i)));
            }
            return result;
        }

        private ClosureCondition convertConditionAnd(
                final MALParser.ClosureConditionAndContext ctx) {
            ClosureCondition result = convertConditionPrimary(ctx.closureConditionPrimary(0));
            for (int i = 1; i < ctx.closureConditionPrimary().size(); i++) {
                result = new MALExpressionModel.ClosureLogical(
                    result, LogicalOp.AND,
                    convertConditionPrimary(ctx.closureConditionPrimary(i)));
            }
            return result;
        }

        private ClosureCondition convertConditionPrimary(
                final MALParser.ClosureConditionPrimaryContext ctx) {
            if (ctx instanceof MALParser.ConditionEqContext) {
                final MALParser.ConditionEqContext eq = (MALParser.ConditionEqContext) ctx;
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(eq.closureExpr(0)),
                    CompareOp.EQ,
                    convertClosureExpr(eq.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ConditionNeqContext) {
                final MALParser.ConditionNeqContext neq = (MALParser.ConditionNeqContext) ctx;
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(neq.closureExpr(0)),
                    CompareOp.NEQ,
                    convertClosureExpr(neq.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ConditionGtContext) {
                final MALParser.ConditionGtContext gt = (MALParser.ConditionGtContext) ctx;
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(gt.closureExpr(0)),
                    CompareOp.GT,
                    convertClosureExpr(gt.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ConditionLtContext) {
                final MALParser.ConditionLtContext lt = (MALParser.ConditionLtContext) ctx;
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(lt.closureExpr(0)),
                    CompareOp.LT,
                    convertClosureExpr(lt.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ConditionNotContext) {
                final MALParser.ConditionNotContext not = (MALParser.ConditionNotContext) ctx;
                return new MALExpressionModel.ClosureNot(
                    convertConditionPrimary(not.closureConditionPrimary()));
            }
            if (ctx instanceof MALParser.ConditionInContext) {
                final MALParser.ConditionInContext in = (MALParser.ConditionInContext) ctx;
                final List<String> values = new ArrayList<>();
                if (in.closureListLiteral() != null) {
                    in.closureListLiteral().STRING().forEach(
                        s -> values.add(stripQuotes(s.getText())));
                }
                return new MALExpressionModel.ClosureInCondition(
                    convertClosureExpr(in.closureExpr()), values);
            }
            if (ctx instanceof MALParser.ConditionParenContext) {
                final MALParser.ConditionParenContext paren =
                    (MALParser.ConditionParenContext) ctx;
                return convertCondition(paren.closureCondition());
            }
            // conditionExpr
            final MALParser.ConditionExprContext exprCtx =
                (MALParser.ConditionExprContext) ctx;
            return new ClosureExprCondition(convertClosureExpr(exprCtx.closureExpr()));
        }

        /**
         * Infer the Java type for a {@code def} variable declaration from its initializer.
         * <ul>
         *   <li>Regex match ({@code =~}) produces {@code String[][]}</li>
         *   <li>Method chain ending in {@code .split()} produces {@code String[]}</li>
         *   <li>Otherwise defaults to {@code Object}</li>
         * </ul>
         */
        private String inferDefType(final ClosureExpr init) {
            if (init instanceof MALExpressionModel.ClosureRegexMatchExpr) {
                return "String[][]";
            }
            final List<MALExpressionModel.ClosureChainSegment> segs;
            if (init instanceof ClosureMethodChain) {
                segs = ((ClosureMethodChain) init).getSegments();
            } else if (init instanceof MALExpressionModel.ClosureExprChain) {
                segs = ((MALExpressionModel.ClosureExprChain) init).getSegments();
            } else {
                segs = Collections.emptyList();
            }
            if (!segs.isEmpty()) {
                final MALExpressionModel.ClosureChainSegment last =
                    segs.get(segs.size() - 1);
                if (last instanceof MALExpressionModel.ClosureMethodCallSeg
                        && "split".equals(
                            ((MALExpressionModel.ClosureMethodCallSeg) last).getName())) {
                    return "String[]";
                }
            }
            return "Object";
        }

        private CompareOp convertCompOp(final MALParser.CompOpContext ctx) {
            if (ctx.GT() != null) {
                return CompareOp.GT;
            }
            if (ctx.LT() != null) {
                return CompareOp.LT;
            }
            if (ctx.GTE() != null) {
                return CompareOp.GTE;
            }
            if (ctx.LTE() != null) {
                return CompareOp.LTE;
            }
            if (ctx.DEQ() != null) {
                return CompareOp.EQ;
            }
            return CompareOp.NEQ;
        }

        private ClosureExpr convertClosureExpr(final MALParser.ClosureExprContext ctx) {
            if (ctx instanceof MALParser.ClosureTernaryCompContext) {
                final MALParser.ClosureTernaryCompContext tc =
                    (MALParser.ClosureTernaryCompContext) ctx;
                return new MALExpressionModel.ClosureCompTernaryExpr(
                    convertClosureExpr(tc.closureExpr(0)),
                    convertCompOp(tc.compOp()),
                    convertClosureExpr(tc.closureExpr(1)),
                    convertClosureExpr(tc.closureExpr(2)),
                    convertClosureExpr(tc.closureExpr(3)));
            }
            if (ctx instanceof MALParser.ClosureTernaryContext) {
                final MALParser.ClosureTernaryContext ternary =
                    (MALParser.ClosureTernaryContext) ctx;
                return new MALExpressionModel.ClosureTernaryExpr(
                    convertClosureExpr(ternary.closureExpr(0)),
                    convertClosureExpr(ternary.closureExpr(1)),
                    convertClosureExpr(ternary.closureExpr(2)));
            }
            if (ctx instanceof MALParser.ClosureRegexMatchContext) {
                final MALParser.ClosureRegexMatchContext rm =
                    (MALParser.ClosureRegexMatchContext) ctx;
                final String rawRegex = rm.REGEX_LITERAL().getText();
                // Strip surrounding slashes: /pattern/ → pattern
                final String pattern = rawRegex.substring(1, rawRegex.length() - 1);
                return new MALExpressionModel.ClosureRegexMatchExpr(
                    convertClosureExpr(rm.closureExpr()), pattern);
            }
            if (ctx instanceof MALParser.ClosureElvisContext) {
                final MALParser.ClosureElvisContext elvis =
                    (MALParser.ClosureElvisContext) ctx;
                return new MALExpressionModel.ClosureElvisExpr(
                    convertClosureExpr(elvis.closureExpr(0)),
                    convertClosureExpr(elvis.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureAddContext) {
                final MALParser.ClosureAddContext add = (MALParser.ClosureAddContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(add.closureExpr(0)),
                    ArithmeticOp.ADD,
                    convertClosureExpr(add.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureSubContext) {
                final MALParser.ClosureSubContext sub = (MALParser.ClosureSubContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(sub.closureExpr(0)),
                    ArithmeticOp.SUB,
                    convertClosureExpr(sub.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureMulContext) {
                final MALParser.ClosureMulContext mul = (MALParser.ClosureMulContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(mul.closureExpr(0)),
                    ArithmeticOp.MUL,
                    convertClosureExpr(mul.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureDivContext) {
                final MALParser.ClosureDivContext div = (MALParser.ClosureDivContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(div.closureExpr(0)),
                    ArithmeticOp.DIV,
                    convertClosureExpr(div.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureUnaryMinusContext) {
                final MALParser.ClosureUnaryMinusContext um =
                    (MALParser.ClosureUnaryMinusContext) ctx;
                final ClosureExpr inner =
                    convertClosureExprPrimary(um.closureExprPrimary());
                if (inner instanceof ClosureNumberLiteral) {
                    return new ClosureNumberLiteral(
                        -((ClosureNumberLiteral) inner).getValue());
                }
                return new ClosureBinaryExpr(
                    new ClosureNumberLiteral(0),
                    ArithmeticOp.SUB,
                    inner);
            }
            // closurePrimary
            final MALParser.ClosurePrimaryContext primary =
                (MALParser.ClosurePrimaryContext) ctx;
            return convertClosureExprPrimary(primary.closureExprPrimary());
        }

        private ClosureExpr convertClosureExprPrimary(
                final MALParser.ClosureExprPrimaryContext ctx) {
            if (ctx instanceof MALParser.ClosureStringContext) {
                final MALParser.ClosureStringContext sc =
                    (MALParser.ClosureStringContext) ctx;
                final String raw = stripQuotes(sc.STRING().getText());
                final ClosureExpr base = expandGString(raw);
                return wrapWithChainAccess(base, sc.closureChainAccess());
            }
            if (ctx instanceof MALParser.ClosureNumberContext) {
                return new ClosureNumberLiteral(
                    Double.parseDouble(
                        ((MALParser.ClosureNumberContext) ctx).NUMBER().getText()));
            }
            if (ctx instanceof MALParser.ClosureNullContext) {
                return new ClosureNullLiteral();
            }
            if (ctx instanceof MALParser.ClosureBoolContext) {
                final MALParser.ClosureBoolContext bc = (MALParser.ClosureBoolContext) ctx;
                return new ClosureBoolLiteral(bc.boolLiteral().TRUE() != null);
            }
            if (ctx instanceof MALParser.ClosureParenContext) {
                final MALParser.ClosureParenContext pc =
                    (MALParser.ClosureParenContext) ctx;
                final ClosureExpr base = convertClosureExpr(pc.closureExpr());
                return wrapWithChainAccess(base, pc.closureChainAccess());
            }
            if (ctx instanceof MALParser.ClosureMapContext) {
                final MALParser.ClosureMapLiteralContext mapCtx =
                    ((MALParser.ClosureMapContext) ctx).closureMapLiteral();
                final List<MALExpressionModel.MapEntry> entries = new ArrayList<>();
                for (final MALParser.ClosureMapEntryContext entry :
                        mapCtx.closureMapEntry()) {
                    entries.add(new MALExpressionModel.MapEntry(
                        stripQuotes(entry.STRING().getText()),
                        convertClosureExpr(entry.closureExpr())));
                }
                return new MALExpressionModel.ClosureMapLiteral(entries);
            }
            // closureChain
            final MALParser.ClosureChainContext chain = (MALParser.ClosureChainContext) ctx;
            return convertClosureMethodChain(chain.closureMethodChain());
        }

        private ClosureMethodChain convertClosureMethodChain(
                final MALParser.ClosureMethodChainContext ctx) {
            final String target = ctx.closureTarget().IDENTIFIER().getText();
            final List<ClosureChainSegment> segments = new ArrayList<>();

            for (final MALParser.ClosureChainAccessContext acc : ctx.closureChainAccess()) {
                if (acc.closureChainSegment() != null) {
                    final boolean isSafeNav = acc.safeNav() != null;
                    segments.add(convertClosureChainSegment(
                        acc.closureChainSegment(), isSafeNav));
                } else if (acc.closureExpr() != null) {
                    // Direct bracket access: tags['key'] or tags[expr]
                    segments.add(new ClosureIndexAccess(
                        convertClosureExpr(acc.closureExpr())));
                }
            }

            return new ClosureMethodChain(target, segments);
        }

        private ClosureExpr wrapWithChainAccess(
                final ClosureExpr base,
                final List<MALParser.ClosureChainAccessContext> accesses) {
            if (accesses == null || accesses.isEmpty()) {
                return base;
            }
            final List<ClosureChainSegment> segments = new ArrayList<>();
            for (final MALParser.ClosureChainAccessContext acc : accesses) {
                if (acc.closureChainSegment() != null) {
                    final boolean isSafeNav = acc.safeNav() != null;
                    segments.add(convertClosureChainSegment(
                        acc.closureChainSegment(), isSafeNav));
                } else if (acc.closureExpr() != null) {
                    segments.add(new ClosureIndexAccess(
                        convertClosureExpr(acc.closureExpr())));
                }
            }
            return new MALExpressionModel.ClosureExprChain(base, segments);
        }

        private ClosureChainSegment convertClosureChainSegment(
                final MALParser.ClosureChainSegmentContext ctx,
                final boolean safeNav) {
            if (ctx instanceof MALParser.ChainMethodCallContext) {
                final MALParser.ChainMethodCallContext mc =
                    (MALParser.ChainMethodCallContext) ctx;
                final List<ClosureExpr> args = new ArrayList<>();
                if (mc.closureArgList() != null) {
                    for (final MALParser.ClosureExprContext argCtx :
                            mc.closureArgList().closureExpr()) {
                        args.add(convertClosureExpr(argCtx));
                    }
                }
                return new ClosureMethodCallSeg(mc.IDENTIFIER().getText(), args, safeNav);
            }
            if (ctx instanceof MALParser.ChainIndexAccessContext) {
                final MALParser.ChainIndexAccessContext idx =
                    (MALParser.ChainIndexAccessContext) ctx;
                return new ClosureIndexAccess(convertClosureExpr(idx.closureExpr()));
            }
            // chainFieldAccess
            final MALParser.ChainFieldAccessContext fa =
                (MALParser.ChainFieldAccessContext) ctx;
            return new ClosureFieldAccess(fa.IDENTIFIER().getText(), safeNav);
        }
    }

    /**
     * Expand Groovy GString interpolation: {@code "text ${expr} more"} becomes
     * a concatenation chain: {@code "text " + expr + " more"}.
     * If the string contains no {@code ${...}} patterns, returns a plain
     * {@link ClosureStringLiteral}.
     */
    static MALExpressionModel.ClosureExpr expandGString(final String raw) {
        if (!raw.contains("${")) {
            return new MALExpressionModel.ClosureStringLiteral(raw);
        }

        final List<MALExpressionModel.ClosureExpr> parts = new ArrayList<>();
        int pos = 0;
        while (pos < raw.length()) {
            final int dollarBrace = raw.indexOf("${", pos);
            if (dollarBrace < 0) {
                // Remaining text
                parts.add(new MALExpressionModel.ClosureStringLiteral(
                    raw.substring(pos)));
                break;
            }
            // Text before ${
            if (dollarBrace > pos) {
                parts.add(new MALExpressionModel.ClosureStringLiteral(
                    raw.substring(pos, dollarBrace)));
            }
            // Find matching }
            int braceDepth = 1;
            int i = dollarBrace + 2;
            while (i < raw.length() && braceDepth > 0) {
                if (raw.charAt(i) == '{') {
                    braceDepth++;
                } else if (raw.charAt(i) == '}') {
                    braceDepth--;
                }
                i++;
            }
            final String innerExpr = raw.substring(dollarBrace + 2, i - 1);
            // Parse the inner expression as a mini closure expression
            parts.add(parseGStringInterpolation(innerExpr));
            pos = i;
        }

        // Build concatenation chain
        MALExpressionModel.ClosureExpr result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = new MALExpressionModel.ClosureBinaryExpr(
                result, MALExpressionModel.ArithmeticOp.ADD, parts.get(i));
        }
        return result;
    }

    /**
     * Parse a GString interpolation expression like {@code tags.service_name}
     * or {@code log.service} into a {@link MALExpressionModel.ClosureMethodChain}.
     */
    private static MALExpressionModel.ClosureExpr parseGStringInterpolation(
            final String expr) {
        // Simple dotted path: tags.service_name, me.serviceName, etc.
        // Split on dots and build a chain
        final String[] dotParts = expr.split("\\.");
        if (dotParts.length == 1) {
            // Bare variable reference
            return new MALExpressionModel.ClosureMethodChain(
                dotParts[0], Collections.emptyList());
        }
        // Build chain: first part is target, rest are field accesses
        final List<MALExpressionModel.ClosureChainSegment> segments = new ArrayList<>();
        for (int i = 1; i < dotParts.length; i++) {
            // Check for method call: name()
            if (dotParts[i].endsWith("()")) {
                final String methodName = dotParts[i].substring(
                    0, dotParts[i].length() - 2);
                segments.add(new MALExpressionModel.ClosureMethodCallSeg(
                    methodName, Collections.emptyList(), false));
            } else if (dotParts[i].endsWith(")")) {
                // Method with args not supported in GString — treat as field
                segments.add(new MALExpressionModel.ClosureFieldAccess(
                    dotParts[i], false));
            } else {
                segments.add(new MALExpressionModel.ClosureFieldAccess(
                    dotParts[i], false));
            }
        }
        return new MALExpressionModel.ClosureMethodChain(dotParts[0], segments);
    }

    static String stripQuotes(final String s) {
        if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
