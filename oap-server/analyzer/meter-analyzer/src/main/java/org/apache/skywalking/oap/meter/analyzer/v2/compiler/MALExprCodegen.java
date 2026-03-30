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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private int closureFieldIndex;
    private int varCounter;
    private final Set<String> declaredVars = new HashSet<>();

    MALExprCodegen(final List<String> closureFieldNames) {
        this.closureFieldNames = closureFieldNames;
        this.chainCodegen = new MALMethodChainCodegen(this);
    }

    int getVarCount() {
        return varCounter;
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
        final String base = "_" + MALCodegenHelper.sanitizeName(metricName);
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
          .append(MALCodegenHelper.escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY));\n");
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
                chainCodegen.generateArgument(sb, var, args.get(i));
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
                chainCodegen.generateArgument(sb, var, args.get(i));
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
        if (expr instanceof MALExpressionModel.NumberExpr) {
            return true;
        }
        if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            return "time".equals(
                ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName());
        }
        return false;
    }

    /**
     * Emits a scalar as a boxed Number: {@code Long.valueOf(100L)} or
     * {@code Double.valueOf(3.14)}.
     */
    private void emitScalarAsNumber(final StringBuilder sb,
                                     final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            MALCodegenHelper.emitNumberValueOf(
                sb, ((MALExpressionModel.NumberExpr) expr).getValue());
        } else {
            sb.append("Double.valueOf(");
            emitScalarRaw(sb, expr);
            sb.append(')');
        }
    }

    /**
     * Emits a raw scalar value (unboxed): number literal or {@code time()} call.
     */
    private void emitScalarRaw(final StringBuilder sb,
                                final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            sb.append(((MALExpressionModel.NumberExpr) expr).getValue());
        } else if (isScalar(expr)) {
            sb.append("(double) java.time.Instant.now().getEpochSecond()");
        }
    }
}
