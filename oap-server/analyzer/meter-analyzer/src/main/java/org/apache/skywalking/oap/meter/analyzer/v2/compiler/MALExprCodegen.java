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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;

/**
 * Generates Java source for the {@code run(Map)} method body from a MAL expression AST.
 *
 * <p>Uses a <b>variable-per-expression</b> model: each metric gets its own named variable
 * (e.g. {@code _metric1}, {@code _metric2}), and chain calls reassign to the same variable.
 * Binary operations combine variables directly. No shared mutable {@code sf} variable.
 *
 * <p>Example for {@code metric1.sum(['svc']) + metric2.avg(['svc']).test::scale(2.0)}:
 * <pre>
 * public SampleFamily run(java.util.Map samples) {
 *   SampleFamily _metric1 = ((SampleFamily) samples.getOrDefault("metric1", SampleFamily.EMPTY));
 *   _metric1 = _metric1.sum(java.util.Arrays.asList(new String[]{"svc"}));
 *   SampleFamily _metric2 = ((SampleFamily) samples.getOrDefault("metric2", SampleFamily.EMPTY));
 *   _metric2 = _metric2.avg(java.util.Arrays.asList(new String[]{"svc"}));
 *   _metric2 = TestMalExtension.scale(_metric2, 2.0);
 *   return _metric1.plus(_metric2);
 * }
 * </pre>
 *
 * <p>Delegates method chain codegen to {@link MALMethodChainCodegen}.
 */
final class MALExprCodegen {

    private static final String SF = MALCodegenHelper.SF;

    private final List<String> closureFieldNames;
    private final MALMethodChainCodegen chainCodegen;
    private final String ruleName;
    private int closureFieldIndex;
    private int varCounter;
    private final Set<String> declaredVars = new HashSet<>();

    MALExprCodegen(final List<String> closureFieldNames) {
        this(closureFieldNames, "");
    }

    /**
     * @param closureFieldNames pre-computed companion class field names
     * @param ruleName          rule identifier emitted as the first argument of every
     *                          {@code MALDebug.captureXxx(...)} probe call site. Empty
     *                          string when the codegen is being driven by a debug-only
     *                          surface ({@link MALClassGenerator#generateSource}) where
     *                          no class is actually compiled — the source view still
     *                          includes the probe lines so debugging that path renders
     *                          correctly.
     */
    MALExprCodegen(final List<String> closureFieldNames, final String ruleName) {
        this.closureFieldNames = closureFieldNames;
        this.ruleName = ruleName == null ? "" : ruleName;
        this.chainCodegen = new MALMethodChainCodegen(this);
    }

    String getRuleName() {
        return ruleName;
    }

    /**
     * Returns all variable names declared during code generation,
     * for building the {@code LocalVariableTable}.
     */
    Set<String> getDeclaredVars() {
        return declaredVars;
    }

    /**
     * Returns the next closure field name (e.g. {@code _tag}, {@code _forEach}).
     * Called by {@link MALMethodChainCodegen} when generating closure arguments.
     */
    String nextClosureFieldName() {
        return closureFieldNames.get(closureFieldIndex++);
    }

    // ==================== Variable naming ====================

    /**
     * Creates a variable name for a metric expression.
     * Uses {@code _metricName} if not taken, otherwise {@code _metricName_2}, etc.
     */
    private String metricVar(final String metricName) {
        final String base = "_" + DslJavaSourceText.toIdentifier(metricName);
        String name = base;
        int suffix = 2;
        while (declaredVars.contains(name)) {
            name = base + "_" + suffix++;
        }
        declaredVars.add(name);
        varCounter++;
        return name;
    }

    /**
     * Creates a generic temp variable name ({@code _t0}, {@code _t1}, ...).
     * Used for non-metric expressions (numbers, function calls, parenthesized).
     */
    private String tempVar() {
        final String name = "_t" + varCounter++;
        declaredVars.add(name);
        return name;
    }

    // ==================== Code generation ====================

    /**
     * Generates the complete {@code run(Map)} method source.
     */
    String generateRunMethod(final MALExpressionModel.Expr ast) {
        varCounter = 0;
        closureFieldIndex = 0;
        declaredVars.clear();
        if (exactConstant(ast) != null) {
            throw new IllegalArgumentException("An expression of constants only has no sample family to produce");
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("public ").append(SF)
          .append(" run(java.util.Map samples) {\n");
        final String result = emitExpr(sb, ast);
        sb.append("  return ").append(result).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Emits statements for an expression and returns the variable name holding the result.
     *
     * <p>Each expression type produces its own named variable:
     * <ul>
     *   <li>{@code metric.sum(['svc'])} → declares {@code _metric}, chains on it</li>
     *   <li>{@code 100} → declares {@code _t0 = SampleFamily.EMPTY.plus(100L)}</li>
     *   <li>{@code metric1 + metric2} → emits both, returns combined expression</li>
     *   <li>{@code -metric} → emits metric, returns {@code _metric.negative()}</li>
     * </ul>
     */
    String emitExpr(final StringBuilder sb,
                     final MALExpressionModel.Expr expr) {
        // Where a sample family is due, a number is EMPTY: (2).multiply(3), count(1 + 2), (1 + 2).sum(...).
        if (exactConstant(expr) != null) {
            throw new IllegalArgumentException("A number is no sample family; a method or function on it computes"
                + " nothing: " + describe(expr));
        }
        if (expr instanceof MALExpressionModel.ParenChainExpr
            && exactConstant(((MALExpressionModel.ParenChainExpr) expr).getInner()) != null) {
            throw new IllegalArgumentException("A method on a number computes nothing in MAL: " + describe(expr));
        }
        if (expr instanceof MALExpressionModel.MetricExpr) {
            return emitMetricExpr(sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            return emitNumberExpr(sb, (MALExpressionModel.NumberExpr) expr);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            return emitBinaryExpr(sb, (MALExpressionModel.BinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            return emitUnaryNegExpr(
                sb, (MALExpressionModel.UnaryNegExpr) expr);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            return emitFunctionCallExpr(
                sb, (MALExpressionModel.FunctionCallExpr) expr);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            return emitParenChainExpr(
                sb, (MALExpressionModel.ParenChainExpr) expr);
        } else {
            throw new IllegalArgumentException(
                "Unknown expr type: "
                    + (expr == null ? "null" : expr.getClass().getSimpleName()));
        }
    }

    /**
     * {@code metric.sum(['svc']).rate("PT1M")} →
     * <pre>
     *   SampleFamily _metric = ((SF) samples.getOrDefault("metric", SF.EMPTY));
     *   _metric = _metric.sum(...);
     *   _metric = _metric.rate("PT1M");
     * </pre>
     * Returns {@code "_metric"}.
     */
    private String emitMetricExpr(final StringBuilder sb,
                                   final MALExpressionModel.MetricExpr expr) {
        final String var = metricVar(expr.getMetricName());
        sb.append("  ").append(SF).append(" ").append(var)
          .append(" = ((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(DslJavaSourceText.toLiteral(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY));\n");
        // First read of an input metric — capture the SampleFamily as it enters the chain.
        // The metric name itself is the sourceText: the UI shows "input: <metric>".
        MALCodegenHelper.emitCaptureInput(sb, ruleName, expr.getMetricName(), var);
        chainCodegen.emitChainStatements(sb, var, expr.getMethodChain());
        return var;
    }

    /**
     * {@code 100} →
     * <pre>
     *   SampleFamily _t0 = SF.EMPTY.plus(Long.valueOf(100L));
     * </pre>
     */
    private String emitNumberExpr(final StringBuilder sb,
                                   final MALExpressionModel.NumberExpr expr) {
        final String var = tempVar();
        sb.append("  ").append(SF).append(" ").append(var)
          .append(" = ").append(SF).append(".EMPTY.plus(");
        MALCodegenHelper.emitNumberValueOf(sb, expr.getValue());
        sb.append(");\n");
        return var;
    }

    /**
     * {@code metric1 + metric2} →
     * <pre>
     *   SampleFamily _metric1 = ...;
     *   SampleFamily _metric2 = ...;
     *   _metric1 = _metric1.plus(_metric2);
     * </pre>
     * Returns {@code "_metric1"}.
     *
     * <p>For scalar + SF: {@code 100 * metric} →
     * <pre>
     *   SampleFamily _metric = ...;
     *   _metric = _metric.multiply(Long.valueOf(100L));
     * </pre>
     */
    private String emitBinaryExpr(final StringBuilder sb,
                                   final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();
        final String resultVar = emitBinaryExprBody(sb, left, right, op);
        // Treat each binary op as one stage so the UI sees the value after the operator
        // in the same waterfall column as named chain stages. The operator's method name
        // (plus, minus, multiply, divide) is the verbatim DSL fragment.
        MALCodegenHelper.emitCaptureStage(sb, ruleName,
                                          MALCodegenHelper.opMethodName(op), resultVar);
        return resultVar;
    }

    private String emitBinaryExprBody(final StringBuilder sb,
                                      final MALExpressionModel.Expr left,
                                      final MALExpressionModel.Expr right,
                                      final MALExpressionModel.ArithmeticOp op) {

        final boolean leftIsScalar = isScalar(left);
        final boolean rightIsScalar = isScalar(right);

        if (leftIsScalar && !rightIsScalar) {
            // N op SF
            final String rightVar = emitExpr(sb, right);
            switch (op) {
                case ADD:
                    sb.append("  ").append(rightVar).append(" = ")
                      .append(rightVar).append(".plus(");
                    emitScalarAsNumber(sb, left);
                    sb.append(");\n");
                    break;
                case SUB:
                    sb.append("  ").append(rightVar).append(" = ")
                      .append(rightVar).append(".minus(");
                    emitScalarAsNumber(sb, left);
                    sb.append(").negative();\n");
                    break;
                case MUL:
                    sb.append("  ").append(rightVar).append(" = ")
                      .append(rightVar).append(".multiply(");
                    emitScalarAsNumber(sb, left);
                    sb.append(");\n");
                    break;
                case DIV:
                    sb.append("  ").append(rightVar).append(" = ")
                      .append("org.apache.skywalking.oap.meter.analyzer")
                      .append(".v2.compiler.rt.MalRuntimeHelper.divReverse(");
                    emitScalarRaw(sb, left);
                    sb.append(", ").append(rightVar).append(");\n");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
            return rightVar;
        } else if (!leftIsScalar && rightIsScalar) {
            // SF op N
            final String leftVar = emitExpr(sb, left);
            sb.append("  ").append(leftVar).append(" = ")
              .append(leftVar).append(".")
              .append(MALCodegenHelper.opMethodName(op)).append("(");
            emitScalarAsNumber(sb, right);
            sb.append(");\n");
            return leftVar;
        } else {
            // SF op SF
            final String leftVar = emitExpr(sb, left);
            final String rightVar = emitExpr(sb, right);
            sb.append("  ").append(leftVar).append(" = ")
              .append(leftVar).append(".")
              .append(MALCodegenHelper.opMethodName(op))
              .append("(").append(rightVar).append(");\n");
            return leftVar;
        }
    }

    /**
     * {@code -metric} → emits metric, then {@code _metric = _metric.negative();}
     */
    private String emitUnaryNegExpr(
            final StringBuilder sb,
            final MALExpressionModel.UnaryNegExpr expr) {
        final String var = emitExpr(sb, expr.getOperand());
        sb.append("  ").append(var).append(" = ")
          .append(var).append(".negative();\n");
        MALCodegenHelper.emitCaptureStage(sb, ruleName, "negative", var);
        return var;
    }

    /**
     * {@code count(metric, ['tag'])} → emits metric, then applies count.
     * {@code topN(metric, 10, Order.DES)} → same pattern.
     */
    private String emitFunctionCallExpr(
            final StringBuilder sb,
            final MALExpressionModel.FunctionCallExpr expr) {
        final String fn = expr.getFunctionName();
        final List<MALExpressionModel.Argument> args = expr.getArguments();

        String var;
        if (("count".equals(fn) || "topN".equals(fn)) && !args.isEmpty()) {
            final MALExpressionModel.Argument firstArg = args.get(0);
            if (firstArg instanceof MALExpressionModel.ExprArgument) {
                var = emitExpr(
                    sb, ((MALExpressionModel.ExprArgument) firstArg).getExpr());
            } else {
                var = tempVar();
            }
            sb.append("  ").append(var).append(" = ")
              .append(var).append('.').append(fn).append('(');
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                chainCodegen.generateArgument(sb, var, args.get(i), false);
            }
            sb.append(");\n");
        } else {
            var = tempVar();
            sb.append("  ").append(SF).append(" ").append(var)
              .append(" = ").append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                chainCodegen.generateArgument(sb, var, args.get(i), false);
            }
            sb.append(");\n");
        }
        chainCodegen.emitChainStatements(sb, var, expr.getMethodChain());
        return var;
    }

    /**
     * {@code (metric * 2).sum(['svc'])} → emits inner, then applies chain.
     */
    private String emitParenChainExpr(
            final StringBuilder sb,
            final MALExpressionModel.ParenChainExpr expr) {
        final String var = emitExpr(sb, expr.getInner());
        chainCodegen.emitChainStatements(sb, var, expr.getMethodChain());
        return var;
    }

    // ==================== Scalar helpers ====================

    /**
     * Whether the expression is a scalar (number literal or scalar function like {@code time()}).
     */
    static boolean isScalar(final MALExpressionModel.Expr expr) {
        if (constantValue(expr) != null) {
            return true;
        }
        if (expr instanceof MALExpressionModel.FunctionCallExpr
                && "time".equals(((MALExpressionModel.FunctionCallExpr) expr).getFunctionName())) {
            final MALExpressionModel.FunctionCallExpr time = (MALExpressionModel.FunctionCallExpr) expr;
            if (!time.getArguments().isEmpty() || !time.getMethodChain().isEmpty()) {
                throw new IllegalArgumentException("time() takes no arguments and no method calls");
            }
            return true;
        }
        // Arithmetic over scalars is a scalar: time() * 1000, -time()
        if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            return isScalar(((MALExpressionModel.UnaryNegExpr) expr).getOperand());
        }
        if (expr instanceof MALExpressionModel.ParenChainExpr
                && ((MALExpressionModel.ParenChainExpr) expr).getMethodChain().isEmpty()) {
            return isScalar(((MALExpressionModel.ParenChainExpr) expr).getInner());
        }
        if (expr instanceof MALExpressionModel.BinaryExpr) {
            final MALExpressionModel.BinaryExpr bin = (MALExpressionModel.BinaryExpr) expr;
            return isScalar(bin.getLeft()) && isScalar(bin.getRight());
        }
        return false;
    }

    /**
     * The value of an expression of number literals only ({@code -1}, {@code (1 + 1)}), or null. It is a scalar as a
     * single literal is; emitted as a sample family it would be EMPTY, and {@code metric * -1} EMPTY too.
     */
    static Double constantValue(final MALExpressionModel.Expr expr) {
        final Constant constant = exactConstant(expr);
        return constant == null ? null : constant.value.doubleValue();
    }

    /**
     * A constant as Groovy computes it: integers as int (or long past the int range), a decimal literal or a
     * quotient as an exact decimal. The generated code takes the double of the exact result, as a number meeting a
     * sample family is; a result Groovy would compute differently (an int overflow, a quotient with no exact
     * decimal) is refused.
     */
    static final class Constant {
        final BigDecimal value;
        /** Groovy holds it as an Integer or Long, which an int or long parameter takes. */
        final boolean integral;

        Constant(final BigDecimal value, final boolean integral) {
            this.value = value;
            this.integral = integral;
        }
    }

    static Constant exactConstant(final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            final MALExpressionModel.NumberExpr number = (MALExpressionModel.NumberExpr) expr;
            // The literal as written (1.0000000000000001), not its double.
            return new Constant(number.getExact(), number.isIntegral());
        }
        if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            final Constant operand = exactConstant(((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            if (operand == null) {
                return null;
            }
            final BigDecimal negated = operand.value.negate();
            if (operand.integral && (fitsInt(operand.value) && !fitsInt(negated) || !fitsLong(negated))) {
                throw new IllegalArgumentException("Negating the constant " + operand.value
                    + " overflows as Groovy computes it");
            }
            return new Constant(negated, operand.integral);
        }
        if (expr instanceof MALExpressionModel.ParenChainExpr
                && ((MALExpressionModel.ParenChainExpr) expr).getMethodChain().isEmpty()) {
            return exactConstant(((MALExpressionModel.ParenChainExpr) expr).getInner());
        }
        if (!(expr instanceof MALExpressionModel.BinaryExpr)) {
            return null;
        }
        final MALExpressionModel.BinaryExpr bin = (MALExpressionModel.BinaryExpr) expr;
        final Constant left = exactConstant(bin.getLeft());
        final Constant right = exactConstant(bin.getRight());
        if (left == null || right == null) {
            return null;
        }
        if (bin.getOp() == MALExpressionModel.ArithmeticOp.DIV) {
            if (right.value.signum() == 0) {
                throw new IllegalArgumentException("Division by zero in a constant expression");
            }
            try {
                return new Constant(left.value.divide(right.value), false);
            } catch (final ArithmeticException e) {
                throw new IllegalArgumentException("The constant quotient " + left.value + " / " + right.value
                    + " has no exact decimal, which Groovy rounds differently; write the value", e);
            }
        }
        final BigDecimal result;
        switch (bin.getOp()) {
            case ADD:
                result = left.value.add(right.value);
                break;
            case SUB:
                result = left.value.subtract(right.value);
                break;
            case MUL:
                result = left.value.multiply(right.value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported op: " + bin.getOp());
        }
        if (left.integral && right.integral) {
            final boolean ints = fitsInt(left.value) && fitsInt(right.value);
            if (ints && !fitsInt(result) || !fitsLong(result)) {
                throw new IllegalArgumentException("The constant " + left.value + " " + bin.getOp() + " "
                    + right.value + " overflows " + (ints ? "int" : "long") + " as Groovy computes it");
            }
            return new Constant(result, true);
        }
        return new Constant(result, false);
    }

    private static String describe(final MALExpressionModel.Expr expr) {
        final Constant constant = exactConstant(expr instanceof MALExpressionModel.ParenChainExpr
            ? ((MALExpressionModel.ParenChainExpr) expr).getInner() : expr);
        return constant == null ? expr.getClass().getSimpleName() : constant.value.toPlainString();
    }

    private static boolean fitsInt(final BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
            && value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
    }

    private static boolean fitsLong(final BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0
            && value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
    }

    /**
     * Emits a scalar as a boxed Number: {@code Long.valueOf(100L)} or
     * {@code Double.valueOf(3.14)}.
     */
    private void emitScalarAsNumber(final StringBuilder sb,
                                     final MALExpressionModel.Expr expr) {
        final Double constant = constantValue(expr);
        if (constant != null) {
            MALCodegenHelper.emitNumberValueOf(sb, constant);
        } else {
            sb.append("Double.valueOf(");
            emitScalarRaw(sb, expr);
            sb.append(')');
        }
    }

    /**
     * Emits a raw scalar value (unboxed): number literal or {@code time()} call.
     */
    static void emitScalarRaw(final StringBuilder sb,
                              final MALExpressionModel.Expr expr) {
        final Double constant = constantValue(expr);
        if (constant != null) {
            sb.append(constant.doubleValue());
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr && isScalar(expr)) {
            sb.append("(double) java.time.Instant.now().getEpochSecond()");
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr && isScalar(expr)) {
            sb.append("(-");
            emitScalarRaw(sb, ((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            sb.append(')');
        } else if (expr instanceof MALExpressionModel.ParenChainExpr && isScalar(expr)) {
            emitScalarRaw(sb, ((MALExpressionModel.ParenChainExpr) expr).getInner());
        } else if (expr instanceof MALExpressionModel.BinaryExpr && isScalar(expr)) {
            final MALExpressionModel.BinaryExpr bin = (MALExpressionModel.BinaryExpr) expr;
            if (bin.getOp() == MALExpressionModel.ArithmeticOp.DIV && constantValue(bin.getRight()) == null) {
                // a divisor read at run time (time() - time()) may be zero: throws, as Groovy
                sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".scalarDivide(");
                emitScalarRaw(sb, bin.getLeft());
                sb.append(", ");
                emitScalarRaw(sb, bin.getRight());
                sb.append(')');
                return;
            }
            sb.append('(');
            emitScalarRaw(sb, bin.getLeft());
            switch (bin.getOp()) {
                case ADD:
                    sb.append(" + ");
                    break;
                case SUB:
                    sb.append(" - ");
                    break;
                case MUL:
                    sb.append(" * ");
                    break;
                case DIV:
                    if (Double.valueOf(0).equals(constantValue(bin.getRight()))
                        || Double.valueOf(-0.0).equals(constantValue(bin.getRight()))) {
                        // Groovy throws dividing time() by zero; a double would give Infinity
                        throw new IllegalArgumentException("Division by zero");
                    }
                    sb.append(" / ");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + bin.getOp());
            }
            emitScalarRaw(sb, bin.getRight());
            sb.append(')');
        } else {
            throw new IllegalArgumentException("Not a scalar: " + expr.getClass().getSimpleName());
        }
    }
}
