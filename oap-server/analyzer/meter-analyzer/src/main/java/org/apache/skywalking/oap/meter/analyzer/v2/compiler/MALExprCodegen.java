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

import java.util.List;

/**
 * Generates Java source for the {@code run(Map)} method body from a MAL expression AST.
 *
 * <p>Supports two codegen modes:
 * <ul>
 *   <li><b>Statement-based</b> (for the top-level {@code run()} method): each expression
 *       step is a separate {@code sf = ...;} statement. Used by {@code generateRunMethod()}.
 *       Example for {@code metric.sum(['svc']).rate("PT1M")}:
 *       <pre>
 *         sf = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
 *         sf = sf.sum(Arrays.asList(new String[]{"svc"}));
 *         sf = sf.rate("PT1M");
 *       </pre></li>
 *   <li><b>Inline</b> (for sub-expressions in binary ops, function args): the entire
 *       expression is a single chained Java expression. Used by {@code generateExprInline()}.
 *       Example for {@code metric.sum(['svc'])} as a sub-expression:
 *       {@code ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY)).sum(...)}</li>
 * </ul>
 *
 * <p>Delegates method chain codegen to {@link MALMethodChainCodegen}.
 */
final class MALExprCodegen {

    private static final String SF = MALCodegenHelper.SF;
    private static final String RUN_VAR = MALCodegenHelper.RUN_VAR;

    private final List<String> closureFieldNames;
    private final MALMethodChainCodegen chainCodegen;
    private int closureFieldIndex;
    private int runTempCounter;

    MALExprCodegen(final List<String> closureFieldNames) {
        this.closureFieldNames = closureFieldNames;
        this.chainCodegen = new MALMethodChainCodegen(this);
    }

    int getRunTempCounter() {
        return runTempCounter;
    }

    /**
     * Returns the next closure field name (e.g. {@code _tag}, {@code _forEach}).
     * Called by {@link MALMethodChainCodegen} when generating closure arguments.
     */
    String nextClosureFieldName() {
        return closureFieldNames.get(closureFieldIndex++);
    }

    // ==================== Statement-based codegen ====================

    /**
     * Generates the complete {@code run(Map)} method source.
     *
     * <p>For {@code metric.sum(['svc']).rate("PT1M")}, generates:
     * <p>Note: generated code uses FQCNs (e.g. {@code o.a.s...SampleFamily}) to avoid
     * import issues in Javassist. Simplified here for readability:
     * <pre>
     * public SampleFamily run(java.util.Map samples) {
     *   SampleFamily sf;
     *   sf = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
     *   sf = sf.sum(java.util.Arrays.asList(new String[]{"svc"}));
     *   sf = sf.rate("PT1M");
     *   return sf;
     * }
     * </pre>
     */
    String generateRunMethod(final MALExpressionModel.Expr ast) {
        runTempCounter = 0;
        closureFieldIndex = 0;
        final StringBuilder sb = new StringBuilder();
        sb.append("public ").append(SF).append(" run(java.util.Map samples) {\n");
        sb.append("  ").append(SF).append(" ").append(RUN_VAR).append(";\n");
        generateExprStatements(sb, ast);
        sb.append("  return ").append(RUN_VAR).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String nextTemp() {
        return "_t" + runTempCounter++;
    }

    /**
     * Emits the expression as a series of {@code sf = ...;} reassignment statements.
     *
     * <p>Dispatches by AST node type:
     * <ul>
     *   <li>{@code metric} → lookup from samples map + chain calls</li>
     *   <li>{@code 100} → {@code sf = SampleFamily.EMPTY.plus(Long.valueOf(100L));}</li>
     *   <li>{@code metric1 + metric2} → temp variable for left, compute right, combine</li>
     *   <li>{@code -metric} → {@code sf = sf.negative();}</li>
     *   <li>{@code count(metric, [...])} → function call + chain</li>
     *   <li>{@code (metric * 2).sum([...])} → parenthesized + chain</li>
     * </ul>
     */
    private void generateExprStatements(final StringBuilder sb,
                                         final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            generateMetricExprStatements(
                sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            final double val = ((MALExpressionModel.NumberExpr) expr).getValue();
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(SF).append(".EMPTY.plus(");
            MALCodegenHelper.emitNumberValueOf(sb, val);
            sb.append(");\n");
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            generateBinaryExprStatements(
                sb, (MALExpressionModel.BinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            generateExprStatements(
                sb, ((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append(".negative();\n");
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            generateFunctionCallStatements(
                sb, (MALExpressionModel.FunctionCallExpr) expr);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            generateParenChainStatements(
                sb, (MALExpressionModel.ParenChainExpr) expr);
        } else {
            throw new IllegalArgumentException("Unknown expr type: " + expr);
        }
    }

    /**
     * Generates statements for {@code metric.sum(['svc']).rate("PT1M")}.
     *
     * <pre>
     *   sf = ((SampleFamily) samples.getOrDefault("metric", SampleFamily.EMPTY));
     *   sf = sf.sum(...);
     *   sf = sf.rate("PT1M");
     * </pre>
     */
    private void generateMetricExprStatements(
            final StringBuilder sb,
            final MALExpressionModel.MetricExpr expr) {
        sb.append("  ").append(RUN_VAR).append(" = ((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(MALCodegenHelper.escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY));\n");
        chainCodegen.emitChainStatements(sb, expr.getMethodChain());
    }

    /**
     * Generates statements for {@code (metric * 2).sum([...])}.
     */
    private void generateParenChainStatements(
            final StringBuilder sb,
            final MALExpressionModel.ParenChainExpr expr) {
        generateExprStatements(sb, expr.getInner());
        chainCodegen.emitChainStatements(sb, expr.getMethodChain());
    }

    /**
     * Generates statements for top-level functions: {@code count(metric, ['tag'])},
     * {@code topN(metric, 10, Order.DES)}.
     */
    private void generateFunctionCallStatements(
            final StringBuilder sb,
            final MALExpressionModel.FunctionCallExpr expr) {
        final String fn = expr.getFunctionName();
        final List<MALExpressionModel.Argument> args = expr.getArguments();

        if (("count".equals(fn) || "topN".equals(fn)) && !args.isEmpty()) {
            final MALExpressionModel.Argument firstArg = args.get(0);
            if (firstArg instanceof MALExpressionModel.ExprArgument) {
                generateExprStatements(
                    sb, ((MALExpressionModel.ExprArgument) firstArg).getExpr());
            }
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append('.').append(fn).append('(');
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                chainCodegen.generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        } else {
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                chainCodegen.generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        }
        chainCodegen.emitChainStatements(sb, expr.getMethodChain());
    }

    /**
     * Generates statements for binary arithmetic: {@code metric * 100},
     * {@code metric1 + metric2}, {@code 2 / metric}.
     *
     * <p>Three cases:
     * <ul>
     *   <li>N op SF: {@code sf = sf.plus(Long.valueOf(100L));} (swapped)</li>
     *   <li>SF op N: {@code sf = sf.multiply(Long.valueOf(100L));}</li>
     *   <li>SF op SF: uses temp variable {@code _t0} for left operand</li>
     * </ul>
     */
    private void generateBinaryExprStatements(
            final StringBuilder sb,
            final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();

        final boolean leftIsNumber = isScalar(left);
        final boolean rightIsNumber = isScalar(right);

        if (leftIsNumber && !rightIsNumber) {
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ");
            switch (op) {
                case ADD:
                    sb.append(RUN_VAR).append(".plus(");
                    generateScalarExprAsNumber(sb, left);
                    sb.append(')');
                    break;
                case SUB:
                    sb.append(RUN_VAR).append(".minus(");
                    generateScalarExprAsNumber(sb, left);
                    sb.append(").negative()");
                    break;
                case MUL:
                    sb.append(RUN_VAR).append(".multiply(");
                    generateScalarExprAsNumber(sb, left);
                    sb.append(')');
                    break;
                case DIV:
                    sb.append(
                        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt")
                      .append(".MalRuntimeHelper.divReverse(");
                    generateScalarExpr(sb, left);
                    sb.append(", ").append(RUN_VAR).append(")");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
            sb.append(";\n");
        } else if (!leftIsNumber && rightIsNumber) {
            generateExprStatements(sb, left);
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append(".")
              .append(MALCodegenHelper.opMethodName(op)).append('(');
            generateScalarExprAsNumber(sb, right);
            sb.append(");\n");
        } else {
            generateExprStatements(sb, left);
            final String temp = nextTemp();
            sb.append("  ").append(SF).append(" ").append(temp)
              .append(" = ").append(RUN_VAR).append(";\n");
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(temp).append(".")
              .append(MALCodegenHelper.opMethodName(op))
              .append("(").append(RUN_VAR).append(");\n");
        }
    }

    // ==================== Expression pre-computation ====================

    /**
     * Pre-computes a sub-expression to a temp variable via the statement-based path.
     * Used when a complex expression appears as a method argument.
     *
     * <p>For {@code metric2.sum(['svc']).test::scale(2.0)} as an argument, generates:
     * <pre>
     *   SampleFamily _t0 = sf;   // save current sf
     *   sf = ((SampleFamily) samples.getOrDefault("metric2", SampleFamily.EMPTY));
     *   sf = sf.sum(...);
     *   sf = TestMalExtension.scale(sf, 2.0);
     *   SampleFamily _t1 = sf;   // save result
     *   sf = _t0;                // restore original sf
     * </pre>
     * Returns {@code "_t1"} — the temp variable holding the result.
     */
    String preComputeExprToTemp(final StringBuilder sb,
                                 final MALExpressionModel.Expr expr) {
        final String saveCurrent = nextTemp();
        sb.append("  ").append(SF).append(" ").append(saveCurrent)
          .append(" = ").append(RUN_VAR).append(";\n");

        generateExprStatements(sb, expr);

        final String result = nextTemp();
        sb.append("  ").append(SF).append(" ").append(result)
          .append(" = ").append(RUN_VAR).append(";\n");
        sb.append("  ").append(RUN_VAR).append(" = ")
          .append(saveCurrent).append(";\n");

        return result;
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
            final String fn =
                ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            return "time".equals(fn);
        }
        return false;
    }

    /**
     * Emits a raw scalar value: number literal or {@code time()} call.
     * Used inside {@code divReverse()} calls where the raw double is needed.
     */
    private void generateScalarExpr(final StringBuilder sb,
                                     final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            sb.append(((MALExpressionModel.NumberExpr) expr).getValue());
        } else if (isScalar(expr)) {
            final String fn =
                ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            if ("time".equals(fn)) {
                sb.append("(double) java.time.Instant.now().getEpochSecond()");
            }
        }
    }

    /**
     * Emits a scalar expression wrapped with the appropriate {@code Number.valueOf()}.
     * Integer-valued literals use {@code Long.valueOf(NL)}, others use {@code Double.valueOf(N)}.
     */
    private void generateScalarExprAsNumber(final StringBuilder sb,
                                             final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            MALCodegenHelper.emitNumberValueOf(
                sb, ((MALExpressionModel.NumberExpr) expr).getValue());
        } else {
            sb.append("Double.valueOf(");
            generateScalarExpr(sb, expr);
            sb.append(')');
        }
    }
}
