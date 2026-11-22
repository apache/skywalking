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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;
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
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

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
     * Splice {@code expPrefix} into every metric source in {@code exp}.
     *
     * <p>The MAL {@code expPrefix} (e.g. {@code tag({tags -> tags.service = ...})})
     * is a method-call snippet that must apply to every metric reference, not
     * only the first one. A naive string splice at the first dot misses metrics
     * nested in arguments — e.g. the second metric in
     * {@code a.sum(['s']).safeDiv(b.sum(['s']))}.
     *
     * <p>This walker parses {@code exp}, finds every {@code primary} whose child
     * is a bare {@code IDENTIFIER} (i.e. a metric leaf — not a function call or
     * parenthesised group), and replaces it with {@code (<id>.<expPrefix>)}.
     * Downsampling-type constants ({@code SUM}, {@code AVG}, {@code LATEST},
     * {@code SUM_PER_MIN}, {@code MAX}, {@code MIN}) look syntactically identical
     * to metric sources but are recognised by name and skipped — wrapping them
     * with the prefix would turn them into bogus SampleFamily references.
     *
     * @param exp the MAL expression to rewrite (must parse successfully)
     * @param expPrefix the method-call snippet to inject (e.g. {@code tag({...})})
     * @return the rewritten expression, or {@code exp} unchanged when
     *         {@code expPrefix} is null or empty
     */
    public static String injectExpPrefix(final String exp, final String expPrefix) {
        if (expPrefix == null || expPrefix.isEmpty()) {
            return exp;
        }
        // As the compiler reads it: a /regex/ argument is a string to the parser.
        final String source = preprocessRegexLiterals(exp);
        final MALParser.ExpressionContext tree = parse(source, MALParser::expression,
            "MAL expression parsing failed while injecting expPrefix", exp);
        final List<int[]> ranges = new ArrayList<>();
        collectMetricSourceRanges(tree, ranges);
        // Splice from right to left so earlier indices remain valid.
        ranges.sort(Comparator.comparingInt((int[] r) -> r[0]).reversed());
        final StringBuilder sb = new StringBuilder(source);
        for (final int[] range : ranges) {
            // Token indexes count code points; the StringBuilder counts UTF-16 units.
            final int start = source.offsetByCodePoints(0, range[0]);
            final int end = source.offsetByCodePoints(0, range[1] + 1);
            final String name = sb.substring(start, end);
            sb.replace(start, end, "(" + name + "." + expPrefix + ")");
        }
        return sb.toString();
    }

    /**
     * Whether the identifier is the whole argument of a built-in downsampling(...) call, the one place the codegen
     * reads it as a DownsamplingType (MALMethodChainCodegen#generateArgument).
     */
    private static boolean isDownsamplingArgument(final ParseTree primary) {
        for (ParseTree p = primary.getParent(); p != null; p = p.getParent()) {
            if (p instanceof MALParser.ArgumentContext) {
                final ParseTree call = p.getParent() == null ? null : p.getParent().getParent();
                return stripParentheses(p.getText()).equals(primary.getText())
                    && call instanceof MALParser.MethodCallContext
                    && ((MALParser.MethodCallContext) call).DOUBLE_COLON() == null
                    && "downsampling".equals(((MALParser.MethodCallContext) call).IDENTIFIER(0).getText());
            }
        }
        return false;
    }

    private static String stripParentheses(final String text) {
        String s = text;
        while (s.length() > 1 && s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static void collectMetricSourceRanges(final ParseTree node,
                                                  final List<int[]> ranges) {
        if (node instanceof MALParser.PrimaryContext) {
            final MALParser.PrimaryContext primary = (MALParser.PrimaryContext) node;
            // Three primary alternatives: bare IDENTIFIER (metric source),
            // functionCall, and L_PAREN additiveExpression R_PAREN. Only the
            // first is a metric ref.
            if (primary.IDENTIFIER() != null
                    && primary.functionCall() == null
                    && primary.L_PAREN() == null) {
                final TerminalNode id = primary.IDENTIFIER();
                final String text = id.getText();
                // A downsampling constant (SUM, AVG, LATEST, SUM_PER_MIN, MAX,
                // MIN) as the whole argument of .downsampling(MAX) is an enum
                // value to the codegen; anywhere else (MAX.sum(...), count(MAX))
                // the name is a metric and gets the prefix like any other.
                if (!MALCodegenHelper.isDownsamplingType(text) || !isDownsamplingArgument(primary)) {
                    ranges.add(new int[]{
                        id.getSymbol().getStartIndex(),
                        id.getSymbol().getStopIndex()
                    });
                }
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectMetricSourceRanges(node.getChild(i), ranges);
        }
    }

    /**
     * Pre-process expression to convert Groovy regex literals used as method
     * arguments into string literals. E.g., {@code split(/\|/, -1)} becomes
     * {@code split("\\|", -1)}. Regex literals after {@code =~} are handled
     * by the lexer mode and are NOT touched here.
     */
    static String preprocessRegexLiterals(final String expression) {
        // A /pattern/ right after ( or , (method arg context) becomes a single-quoted string, which Groovy does not
        // interpolate either. Quoted strings, comments and a regex after =~ (the lexer's) are copied as they are.
        final StringBuilder sb = new StringBuilder(expression.length());
        char quote = 0;
        char lastSignificant = 0;
        int i = 0;
        while (i < expression.length()) {
            final char c = expression.charAt(i);
            if (quote != 0) {
                sb.append(c);
                if (c == '\\' && i + 1 < expression.length()) {
                    sb.append(expression.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                    lastSignificant = c;
                }
                i++;
                continue;
            }
            final char next = i + 1 < expression.length() ? expression.charAt(i + 1) : 0;
            if (c == '/' && next == '*') {
                final int close = expression.indexOf("*/", i + 2);
                final int stop = close < 0 ? expression.length() : close + 2;
                sb.append(expression, i, stop);
                i = stop;
                continue;
            }
            if (c == '/' && next == '/') {
                int stop = i;
                while (stop < expression.length() && expression.charAt(stop) != '\n') {
                    stop++;
                }
                sb.append(expression, i, stop);
                i = stop;
                continue;
            }
            if (c == '/' && (lastSignificant == ',' || lastSignificant == '(' || lastSignificant == '~')) {
                final int close = regexLiteralEnd(expression, i + 1);
                if (close > i + 1) {
                    requireNoSlashyInterpolation(expression.substring(i, close + 1));
                    if (lastSignificant == '~') {
                        sb.append(expression, i, close + 1);
                    } else {
                        // A string literal of the pattern: a backslash and a quote escaped for the MAL lexer, an
                        // escaped slash unescaped.
                        sb.append('\'').append(expression.substring(i + 1, close).replace("\\/", "/")
                            .replace("\\", "\\\\").replace("'", "\\'")).append('\'');
                    }
                    i = close + 1;
                    lastSignificant = '/';
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                quote = c;
            }
            sb.append(c);
            if (!Character.isWhitespace(c)) {
                lastSignificant = c;
            }
            i++;
        }
        return sb.toString();
    }

    /**
     * Groovy interpolates {@code $name} and {@code ${...}} in a slashy string; a pattern here is taken as written.
     */
    static void requireNoSlashyInterpolation(final String slashy) {
        for (int i = 0; i < slashy.length() - 1; i++) {
            // A backslash escapes only the slash in a slashy string: /\$p/ still interpolates p.
            if (slashy.charAt(i) == '\\' && slashy.charAt(i + 1) == '/') {
                i++;
            } else if (DslStrings.interpolationAt(slashy, i)) {
                throw new IllegalArgumentException("Interpolation in the slashy string " + slashy + " is not supported");
            }
        }
    }

    /**
     * @return the index of the / that closes a regex literal whose body starts at {@code from} ({@code \\/} does not),
     *     or -1 when none does before the line ends
     */
    private static int regexLiteralEnd(final String expression, final int from) {
        for (int i = from; i < expression.length(); i++) {
            final char c = expression.charAt(i);
            if (c == '\\' && i + 1 < expression.length() && expression.charAt(i + 1) == '/') {
                i++;
            } else if (c == '/') {
                return i;
            } else if (c == '\r' || c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    public static Expr parse(final String expression) {
        final MALParser.ExpressionContext tree = parse(preprocessRegexLiterals(expression), MALParser::expression,
            "MAL expression parsing failed", expression);
        return new MALExprVisitor().visit(tree.additiveExpression());
    }

    /**
     * Parse a standalone filter closure expression into a {@link ClosureArgument}.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     */
    public static ClosureArgument parseFilter(final String filterExpression) {
        final MALParser.FilterExpressionContext tree = parse(preprocessRegexLiterals(filterExpression),
            MALParser::filterExpression, "MAL filter expression parsing failed", filterExpression);
        return new ClosureVisitor().visitClosure(tree.closureExpression());
    }

    /**
     * Parses a source by one rule of the grammar; any syntax error the lexer or the parser reports fails it.
     *
     * @param failure  how the message starts
     * @param original the expression as written, for the message
     */
    private static <T> T parse(final String source, final Function<MALParser, T> rule, final String failure,
                               final String original) {
        final MALLexer lexer = new MALLexer(CharStreams.fromString(source));
        final MALParser parser = new MALParser(new CommonTokenStream(lexer));
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
        final T tree = rule.apply(parser);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                failure + ": " + String.join("; ", errors) + " in expression: " + original);
        }
        return tree;
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
            final String text = ctx.NUMBER().getText();
            // A scalar meets a sample family as a double; folding and extension arguments use the text.
            return new NumberExpr(MALLiterals.parseLiteral(text), text.indexOf('.') < 0,
                MALLiterals.exactValue(text));
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
            final List<TerminalNode> ids = ctx.IDENTIFIER();
            final String namespace;
            final String name;
            if (ids.size() == 2) {
                namespace = ids.get(0).getText();
                name = ids.get(1).getText();
            } else {
                namespace = null;
                name = ids.get(0).getText();
            }
            final List<Argument> args = ctx.argumentList() != null
                ? visitArgList(ctx.argumentList()) : Collections.emptyList();
            // Verbatim ANTLR slice of the call (e.g. "sum(['service_name', 'step'])").
            // Pulled from the input stream so whitespace and identifier spelling are
            // exactly what the operator wrote. Used as the captured stage's sourceText.
            return new MethodCall(namespace, name, args,
                ctx.getStart().getLine(), rawTextOf(ctx));
        }

        /**
         * Verbatim source slice of an ANTLR rule context — pulls from the
         * original input stream rather than re-concatenating tokens, so
         * whitespace inside the call is preserved.
         */
        private static String rawTextOf(final ParserRuleContext ctx) {
            if (ctx.getStart() == null || ctx.getStop() == null) {
                return "";
            }
            return ctx.getStart().getInputStream().getText(
                Interval.of(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
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
                return new StringArgument(MALLiterals.literalText(ctx.STRING().getText()));
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
            ctx.STRING().forEach(s -> values.add(MALLiterals.literalText(s.getText())));
            return new StringListArgument(values);
        }

        private NumberListArgument convertNumberList(final MALParser.NumberListContext ctx) {
            final List<Double> values = new ArrayList<>();
            ctx.NUMBER().forEach(n -> values.add(MALLiterals.parseListNumber(n.getText())));
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
                if (ctx.returnStatement().closureCondition() == null) {
                    return new ClosureReturnStatement(null);
                }
                if (ctx.returnStatement().closureCondition().getStart().getLine()
                    != ctx.returnStatement().RETURN().getSymbol().getLine()) {
                    // Groovy ends a bare return at the line break: the next line is a statement of its own.
                    throw new IllegalArgumentException("A return whose value starts on the next line is ambiguous: "
                        + "put the value on the return's line, or end the return with ;");
                }
                final ClosureCondition value = convertCondition(ctx.returnStatement().closureCondition());
                return new ClosureReturnStatement(value instanceof ClosureExprCondition
                    ? ((ClosureExprCondition) value).getExpr() : value);
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
                    // the code generator infers its type from the initializer and keeps it a def (it may be
                    // reassigned a value of another type)
                    typeName = "def";
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
                if (ids.size() > 2 || (ids.size() == 2 && fa.closureExpr() != null)) {
                    throw new IllegalArgumentException("Cannot assign to " + fa.getText()
                        + ": the target is a variable, <map>.<key> or <map>[<key>]");
                }
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
            if (ctx.expressionStatement() == null) {
                throw new IllegalArgumentException("Unsupported closure statement: " + ctx.getText());
            }
            return new ClosureExprStatement(convertCondition(ctx.expressionStatement().closureCondition()));
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

        /**
         * Groovy's ?: binds looser than &&, || and in (a || b ? x : y is (a || b) ? x : y), where this grammar puts
         * a ternary inside the operand: refuse the unparenthesized mix.
         */
        private void requireNoTernaryOperand(final MALParser.ClosureConditionPrimaryContext primary,
                                             final String operator) {
            if (primary instanceof MALParser.ConditionExprContext
                && ((MALParser.ConditionExprContext) primary).closureExpr() instanceof MALParser.ClosureTernaryContext) {
                throw new IllegalArgumentException("A ternary or ?: as an operand of " + operator
                    + " is ambiguous: parenthesize it");
            }
        }

        private ClosureCondition convertConditionOr(
                final MALParser.ClosureConditionOrContext ctx) {
            if (ctx.closureConditionAnd().size() > 1) {
                ctx.closureConditionAnd().forEach(and -> and.closureConditionPrimary().forEach(
                    primary -> requireNoTernaryOperand(primary, "||")));
            }
            ClosureCondition result = convertConditionAnd(ctx.closureConditionAnd(0));
            for (int i = 1; i < ctx.closureConditionAnd().size(); i++) {
                result = new MALExpressionModel.ClosureLogical(
                    result, LogicalOp.OR, convertConditionAnd(ctx.closureConditionAnd(i)));
            }
            return result;
        }

        private ClosureCondition convertConditionAnd(
                final MALParser.ClosureConditionAndContext ctx) {
            if (ctx.closureConditionPrimary().size() > 1) {
                ctx.closureConditionPrimary().forEach(primary -> requireNoTernaryOperand(primary, "&&"));
            }
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
            if (ctx instanceof MALParser.ConditionNotContext) {
                final MALParser.ConditionNotContext not = (MALParser.ConditionNotContext) ctx;
                final MALParser.ClosureConditionPrimaryContext operand = not.closureConditionPrimary();
                // Groovy applies ! to the primary: !a == b is (!a) == b and !a ? x : y is (!a) ? x : y, where the
                // grammar reads !(a == b). Refuse to guess.
                if (!(operand instanceof MALParser.ConditionParenContext)
                        && !(operand instanceof MALParser.ConditionNotContext)
                        && !(operand instanceof MALParser.ConditionExprContext
                            && isPrimaryOperand(((MALParser.ConditionExprContext) operand).closureExpr()))) {
                    throw new IllegalArgumentException("Ambiguous negation " + not.getText()
                        + ": Groovy applies ! to the first operand alone; write !(...) around what it negates");
                }
                return new MALExpressionModel.ClosureNot(convertConditionPrimary(operand));
            }
            if (ctx instanceof MALParser.ConditionInContext) {
                final MALParser.ConditionInContext in = (MALParser.ConditionInContext) ctx;
                if (in.closureExpr() instanceof MALParser.ClosureTernaryContext
                    || in.closureExpr() instanceof MALParser.ClosureRelationalContext
                    || in.closureExpr() instanceof MALParser.ClosureEqualityContext) {
                    // Groovy's in binds tighter than == and ?: (false == x in [...] is false == (x in [...])).
                    throw new IllegalArgumentException("A comparison or ternary before in is ambiguous: parenthesize it");
                }
                final List<String> values = new ArrayList<>();
                if (in.closureListLiteral() != null) {
                    in.closureListLiteral().STRING().forEach(
                        s -> values.add(MALLiterals.literalText(s.getText())));
                }
                return new MALExpressionModel.ClosureInCondition(
                    convertClosureExpr(in.closureExpr()), values);
            }
            if (ctx instanceof MALParser.ConditionParenContext) {
                final MALParser.ConditionParenContext paren =
                    (MALParser.ConditionParenContext) ctx;
                return convertCondition(paren.closureCondition());
            }
            if (!(ctx instanceof MALParser.ConditionExprContext)) {
                throw new IllegalArgumentException("Unsupported closure condition: " + ctx.getText());
            }
            final MALParser.ConditionExprContext exprCtx =
                (MALParser.ConditionExprContext) ctx;
            final ClosureExpr expr = convertClosureExpr(exprCtx.closureExpr());
            return expr instanceof ClosureCondition ? (ClosureCondition) expr : new ClosureExprCondition(expr);
        }

        private boolean isPrimaryOperand(final MALParser.ClosureExprContext ctx) {
            return ctx instanceof MALParser.ClosurePrimaryContext || ctx instanceof MALParser.ClosureUnaryMinusContext;
        }

        /**
         * Groovy groups `a != b =~ /re/` as `(a != b) =~ /re/`: a regex match beside a comparison must be parenthesized.
         */
        private void requireNoRegexOperand(final MALParser.ClosureExprContext left,
                                           final MALParser.ClosureExprContext right) {
            if (left instanceof MALParser.ClosureRegexMatchContext || right instanceof MALParser.ClosureRegexMatchContext) {
                throw new IllegalArgumentException("A regex match beside a comparison is grouped differently by Groovy; "
                    + "parenthesize it: a != (b =~ /re/)");
            }
        }

        private CompareOp compareOp(final int token) {
            switch (token) {
                case MALParser.GT:
                    return CompareOp.GT;
                case MALParser.LT:
                    return CompareOp.LT;
                case MALParser.GTE:
                    return CompareOp.GTE;
                case MALParser.LTE:
                    return CompareOp.LTE;
                case MALParser.DEQ:
                    return CompareOp.EQ;
                case MALParser.NEQ:
                    return CompareOp.NEQ;
                default:
                    throw new IllegalArgumentException("Unsupported comparison token " + token);
            }
        }

        private ClosureExpr convertClosureExpr(final MALParser.ClosureExprContext ctx) {
            if (ctx instanceof MALParser.ClosureRelationalContext) {
                final MALParser.ClosureRelationalContext cmp = (MALParser.ClosureRelationalContext) ctx;
                requireNoRegexOperand(cmp.closureExpr(0), cmp.closureExpr(1));
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(cmp.closureExpr(0)),
                    compareOp(cmp.op.getType()),
                    convertClosureExpr(cmp.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureEqualityContext) {
                final MALParser.ClosureEqualityContext cmp = (MALParser.ClosureEqualityContext) ctx;
                requireNoRegexOperand(cmp.closureExpr(0), cmp.closureExpr(1));
                return new MALExpressionModel.ClosureComparison(
                    convertClosureExpr(cmp.closureExpr(0)),
                    compareOp(cmp.op.getType()),
                    convertClosureExpr(cmp.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureTernaryContext) {
                final MALParser.ClosureTernaryContext ternary =
                    (MALParser.ClosureTernaryContext) ctx;
                if (ternary.closureExpr().size() == 2) {
                    return new MALExpressionModel.ClosureElvisExpr(
                        convertClosureExpr(ternary.closureExpr(0)),
                        convertClosureExpr(ternary.closureExpr(1)));
                }
                return new MALExpressionModel.ClosureTernaryExpr(
                    convertClosureExpr(ternary.closureExpr(0)),
                    convertClosureExpr(ternary.closureExpr(1)),
                    convertClosureExpr(ternary.closureExpr(2)));
            }
            if (ctx instanceof MALParser.ClosureRegexMatchContext) {
                final MALParser.ClosureRegexMatchContext rm =
                    (MALParser.ClosureRegexMatchContext) ctx;
                final String rawRegex = rm.REGEX_LITERAL().getText();
                requireNoSlashyInterpolation(rawRegex);
                // Strip surrounding slashes: /pattern/ → pattern
                // In a slashy string \/ is the slash itself.
                final String pattern = rawRegex.substring(1, rawRegex.length() - 1).replace("\\/", "/");
                return new MALExpressionModel.ClosureRegexMatchExpr(
                    convertClosureExpr(rm.closureExpr()), pattern);
            }
            if (ctx instanceof MALParser.ClosureAddSubContext) {
                final MALParser.ClosureAddSubContext addSub = (MALParser.ClosureAddSubContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(addSub.closureExpr(0)),
                    addSub.op.getType() == MALParser.PLUS ? ArithmeticOp.ADD : ArithmeticOp.SUB,
                    convertClosureExpr(addSub.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureMulDivContext) {
                final MALParser.ClosureMulDivContext mulDiv = (MALParser.ClosureMulDivContext) ctx;
                return new ClosureBinaryExpr(
                    convertClosureExpr(mulDiv.closureExpr(0)),
                    mulDiv.op.getType() == MALParser.STAR ? ArithmeticOp.MUL : ArithmeticOp.DIV,
                    convertClosureExpr(mulDiv.closureExpr(1)));
            }
            if (ctx instanceof MALParser.ClosureUnaryMinusContext) {
                final MALParser.ClosureUnaryMinusContext um =
                    (MALParser.ClosureUnaryMinusContext) ctx;
                if ("9223372036854775808".equals(um.closureExprPrimary().getText())) {
                    // Long.MIN_VALUE: the one literal a long holds only negated
                    return new ClosureNumberLiteral(Long.MIN_VALUE, true);
                }
                final ClosureExpr inner =
                    convertClosureExprPrimary(um.closureExprPrimary());
                if (inner instanceof ClosureNumberLiteral) {
                    return new ClosureNumberLiteral(-((ClosureNumberLiteral) inner).getValue(),
                        ((ClosureNumberLiteral) inner).isIntegral());
                }
                return new MALExpressionModel.ClosureUnaryNegExpr(inner);
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
                final String text = sc.STRING().getText();
                // Groovy interpolates double-quoted strings only, and decides it on the text as written.
                final ClosureExpr base = text.charAt(0) == '"'
                    ? expandGString(text.substring(1, text.length() - 1))
                    : new ClosureStringLiteral(MALLiterals.stripQuotes(text));
                return wrapWithChainAccess(base, sc.closureChainAccess());
            }
            if (ctx instanceof MALParser.ClosureNumberContext) {
                final String text = ((MALParser.ClosureNumberContext) ctx).NUMBER().getText();
                // A decimal computes in double, beside a whole number too (seconds * 1000.0); a quotient with it is a
                // double. (Groovy's BigDecimal differs in precision and printing: 2.50 prints 2.5 here.)
                if (text.indexOf('.') >= 0) {
                    return new ClosureNumberLiteral(MALLiterals.parseLiteral(text), false);
                }
                final double value = MALLiterals.parseNumber(text);
                if (MALLiterals.exactValue(text).compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                    throw new IllegalArgumentException("The literal " + text + " is beyond a long");
                }
                return new ClosureNumberLiteral(value, true);
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
                        MALLiterals.literalText(entry.STRING().getText()),
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
                if (safeNav) {
                    throw new IllegalArgumentException("?.[...] is not supported: " + ctx.getText());
                }
                final MALParser.ChainIndexAccessContext idx =
                    (MALParser.ChainIndexAccessContext) ctx;
                return new ClosureIndexAccess(convertClosureExpr(idx.closureExpr()));
            }
            if (!(ctx instanceof MALParser.ChainFieldAccessContext)) {
                throw new IllegalArgumentException("Unsupported chain segment: " + ctx.getText());
            }
            final MALParser.ChainFieldAccessContext fa =
                (MALParser.ChainFieldAccessContext) ctx;
            return new ClosureFieldAccess(fa.IDENTIFIER().getText(), safeNav);
        }
    }

    /**
     * Expand Groovy GString interpolation over the text of a double-quoted string as written (escapes not yet
     * decoded, so {@code \\044{x}} stays literal as in Groovy): {@code "text ${expr} more"} becomes
     * {@code "" + "text " + expr + " more"}, always a String. An unbraced {@code $name}, which Groovy also
     * interpolates, is refused rather than kept as text.
     */
    static MALExpressionModel.ClosureExpr expandGString(final String rawInner) {
        final List<MALExpressionModel.ClosureExpr> parts = new ArrayList<>();
        final StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < rawInner.length()) {
            final char c = rawInner.charAt(i);
            if (c == '\\' && i + 1 < rawInner.length()) {
                // \$ before what would interpolate is that text; any other escape is decoded with the literal
                if (!DslStrings.interpolationAt(rawInner, i + 1)) {
                    literal.append(c);
                }
                literal.append(rawInner.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '$' && i + 1 < rawInner.length() && rawInner.charAt(i + 1) == '{') {
                int depth = 1;
                int j = i + 2;
                while (j < rawInner.length() && depth > 0) {
                    if (rawInner.charAt(j) == '{') {
                        depth++;
                    } else if (rawInner.charAt(j) == '}') {
                        depth--;
                    }
                    j++;
                }
                if (depth > 0) {
                    throw new IllegalArgumentException("Unterminated ${ in \"" + rawInner + "\"");
                }
                if (literal.length() > 0) {
                    parts.add(new MALExpressionModel.ClosureStringLiteral(MALLiterals.unescapeString(literal.toString())));
                    literal.setLength(0);
                }
                parts.add(new MALExpressionModel.ClosureInterpolation(
                    parseGStringInterpolation(rawInner.substring(i + 2, j - 1))));
                i = j;
                continue;
            }
            if (DslStrings.interpolationAt(rawInner, i)) {
                // ${...} was taken above: an unbraced $name
                throw new IllegalArgumentException("Unbraced interpolation in \"" + rawInner + "\": write ${...}");
            }
            // any other $ ("$2/$1", "US$") is the character, as the rules have always read it
            literal.append(c);
            i++;
        }
        if (literal.length() > 0) {
            parts.add(new MALExpressionModel.ClosureStringLiteral(MALLiterals.unescapeString(literal.toString())));
        }
        if (parts.isEmpty()) {
            return new MALExpressionModel.ClosureStringLiteral("");
        }
        if (parts.size() == 1 && parts.get(0) instanceof MALExpressionModel.ClosureStringLiteral) {
            return parts.get(0);
        }
        // Starting from a string makes every + a concatenation, as the GString is a String even when it opens with,
        // or is only, an interpolated number or null.
        MALExpressionModel.ClosureExpr result = parts.get(0) instanceof MALExpressionModel.ClosureStringLiteral
            ? parts.get(0)
            : new MALExpressionModel.ClosureBinaryExpr(new MALExpressionModel.ClosureStringLiteral(""),
                MALExpressionModel.ArithmeticOp.ADD, parts.get(0));
        for (int k = 1; k < parts.size(); k++) {
            result = new MALExpressionModel.ClosureBinaryExpr(result, MALExpressionModel.ArithmeticOp.ADD, parts.get(k));
        }
        return result;
    }

    /**
     * Parse a GString interpolation expression like {@code tags.service_name}
     * or {@code log.service} into a {@link MALExpressionModel.ClosureMethodChain}.
     */
    private static MALExpressionModel.ClosureExpr parseGStringInterpolation(
            final String body) {
        // Groovy allows spaces around the expression: ${ svc }
        final String expr = body.trim();
        // A variable or a dotted path of fields and no-argument calls: tags.service_name, me.getServiceName()
        if (!expr.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*(\\(\\))?)*")) {
            throw new IllegalArgumentException("Unsupported interpolation ${" + expr
                + "}: only a variable or a dotted path of fields and no-argument calls is interpolated");
        }
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
            } else {
                segments.add(new MALExpressionModel.ClosureFieldAccess(
                    dotParts[i], false));
            }
        }
        return new MALExpressionModel.ClosureMethodChain(dotParts[0], segments);
    }

}
