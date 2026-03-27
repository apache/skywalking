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
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExtensionRegistry;

/**
 * Generates Java source for method chain calls on a named variable.
 *
 * <p>Handles two kinds of method calls in the chain:
 * <ul>
 *   <li><b>Built-in methods</b>: {@code .sum(['tag'])}, {@code .rate("PT1M")}
 *       — generates: {@code _metric = _metric.sum(...);}
 *   </li>
 *   <li><b>Extension methods</b>: {@code .test::scale(2.0)}
 *       — generates: {@code _metric = TestMalExtension.scale(_metric, 2.0);}
 *   </li>
 * </ul>
 *
 * <p>Each method call reassigns to the same variable. Complex expression arguments
 * are pre-computed to temp variables before the method call statement.
 */
final class MALMethodChainCodegen {

    private final MALExprCodegen exprCodegen;

    /**
     * Queue of pre-computed temp variable names for complex expression arguments.
     * Filled before each method call, consumed by {@link #generateArgument} in order.
     */
    private java.util.Queue<String> preComputedArgs;

    MALMethodChainCodegen(final MALExprCodegen exprCodegen) {
        this.exprCodegen = exprCodegen;
    }

    // ==================== Chain codegen ====================

    /**
     * Emits each method chain call as a reassignment of the given variable.
     *
     * <p>For {@code _metric} with chain {@code .sum(['svc']).rate("PT1M")}, generates:
     * <pre>
     *   _metric = _metric.sum(java.util.Arrays.asList(new String[]{"svc"}));
     *   _metric = _metric.rate("PT1M");
     * </pre>
     *
     * <p>For extension calls like {@code .test::scale(2.0)}, generates:
     * <pre>
     *   _metric = TestMalExtension.scale(_metric, 2.0);
     * </pre>
     *
     * @param sb   the output buffer
     * @param var  the variable name to chain on (e.g. {@code "_metric"})
     * @param chain the method calls to emit
     */
    void emitChainStatements(final StringBuilder sb,
                              final String var,
                              final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if (mc.isExtension()) {
                emitExtensionCall(sb, var, mc);
                continue;
            }
            // Pre-compute complex expression arguments to temp variables
            preComputedArgs = preComputeComplexArgs(sb, mc.getArguments());

            sb.append("  ").append(var).append(" = ")
              .append(var).append('.').append(mc.getName()).append('(');
            emitBuiltinArgs(sb, var, mc);
            sb.append(");\n");
            preComputedArgs = null;
        }
    }

    /**
     * Emits arguments for a built-in SampleFamily method call.
     */
    private void emitBuiltinArgs(final StringBuilder sb,
                                  final String var,
                                  final MALExpressionModel.MethodCall mc) {
        final List<MALExpressionModel.Argument> args = mc.getArguments();
        if (MALCodegenHelper.VARARGS_STRING_METHODS.contains(mc.getName())
                && !args.isEmpty() && allStringArgs(args)) {
            sb.append("new String[]{");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateArgument(sb, var, args.get(i));
            }
            sb.append('}');
        } else {
            final boolean primitiveDouble =
                MALCodegenHelper.PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateMethodCallArg(sb, var, args.get(i), primitiveDouble);
            }
        }
    }

    // ==================== Extension function codegen ====================

    /**
     * Emits a direct static method call for an extension function.
     *
     * <p>For {@code .test::scale(3.0)} on variable {@code _metric}, generates:
     * {@code _metric = TestMalExtension.scale(_metric, 3.0);}
     */
    private void emitExtensionCall(final StringBuilder sb,
                                    final String var,
                                    final MALExpressionModel.MethodCall mc) {
        final String ns = mc.getNamespace();
        final String method = mc.getName();
        final MalExtensionRegistry.ExtensionMethod em =
            MalExtensionRegistry.lookup(ns, method);
        if (em == null) {
            throw new IllegalArgumentException(
                "Unknown MAL extension function: " + ns + "::" + method);
        }
        final List<MALExpressionModel.Argument> args = mc.getArguments();
        final int expectedArgs = em.getExtraParamTypes().length;
        if (args.size() != expectedArgs) {
            throw new IllegalArgumentException(
                "MAL extension " + ns + "::" + method + " expects "
                    + expectedArgs + " argument(s), got " + args.size());
        }
        sb.append("  ").append(var).append(" = ")
          .append(em.getDeclaringClass()).append('.')
          .append(em.getMethodName()).append('(')
          .append(var);
        for (int i = 0; i < args.size(); i++) {
            sb.append(", ");
            generateExtensionArg(sb, args.get(i), em.getExtraParamTypes()[i]);
        }
        sb.append(");\n");
    }

    /**
     * Generates a typed argument for an extension function call.
     * Emits raw literals: {@code 3.0}, {@code 10L}, {@code 3.0F}, {@code 10}.
     */
    private void generateExtensionArg(final StringBuilder sb,
                                       final MALExpressionModel.Argument arg,
                                       final Class<?> expectedType) {
        if (expectedType == String.class) {
            if (arg instanceof MALExpressionModel.StringArgument) {
                sb.append('"')
                  .append(MALCodegenHelper.escapeJava(
                      ((MALExpressionModel.StringArgument) arg).getValue()))
                  .append('"');
            } else {
                throw new IllegalArgumentException(
                    "Expected String argument for extension function, got "
                        + arg.getClass().getSimpleName());
            }
        } else if (expectedType == double.class || expectedType == Double.class
                || expectedType == float.class || expectedType == Float.class
                || expectedType == long.class || expectedType == Long.class
                || expectedType == int.class || expectedType == Integer.class) {
            if (!(arg instanceof MALExpressionModel.ExprArgument)) {
                throw new IllegalArgumentException(
                    "Expected number argument for extension function, got "
                        + arg.getClass().getSimpleName());
            }
            final MALExpressionModel.Expr expr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (!(expr instanceof MALExpressionModel.NumberExpr)) {
                throw new IllegalArgumentException(
                    "Expected number argument for extension function");
            }
            final double raw =
                ((MALExpressionModel.NumberExpr) expr).getValue();
            if (expectedType == double.class || expectedType == Double.class) {
                sb.append(raw);
            } else if (expectedType == float.class
                    || expectedType == Float.class) {
                sb.append((float) raw).append('F');
            } else if (expectedType == long.class
                    || expectedType == Long.class) {
                sb.append((long) raw).append('L');
            } else {
                sb.append((int) raw);
            }
        } else if (java.util.List.class.isAssignableFrom(expectedType)) {
            if (arg instanceof MALExpressionModel.StringListArgument) {
                final java.util.List<String> values =
                    ((MALExpressionModel.StringListArgument) arg).getValues();
                sb.append("java.util.Arrays.asList(new String[]{");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append('"')
                      .append(MALCodegenHelper.escapeJava(values.get(i)))
                      .append('"');
                }
                sb.append("})");
            } else {
                throw new IllegalArgumentException(
                    "Expected list argument for extension function, got "
                        + arg.getClass().getSimpleName());
            }
        } else {
            throw new IllegalArgumentException(
                "Unsupported extension parameter type: "
                    + expectedType.getName());
        }
    }

    // ==================== Argument codegen ====================

    /**
     * Generates a method call argument with special handling for primitive double methods.
     * For {@code .valueEqual(33)}: emits raw {@code 33.0}.
     * For {@code .multiply(100)}: emits boxed {@code Long.valueOf(100L)}.
     */
    private void generateMethodCallArg(final StringBuilder sb,
                                        final String var,
                                        final MALExpressionModel.Argument arg,
                                        final boolean primitiveDouble) {
        if (primitiveDouble
                && arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                sb.append(
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue());
                return;
            }
        }
        generateArgument(sb, var, arg);
    }

    /**
     * Generates a general argument (string, string list, number list, bool, null, enum,
     * expression, or closure reference).
     *
     * <p>Complex expression arguments are pre-computed to temp variables by
     * {@link #preComputeComplexArgs} before the method call statement starts.
     *
     * @param sb  the output buffer (inside a method call's argument list)
     * @param var the current chain variable name
     * @param arg the argument to emit
     */
    void generateArgument(final StringBuilder sb,
                           final String var,
                           final MALExpressionModel.Argument arg) {
        if (arg instanceof MALExpressionModel.StringArgument) {
            sb.append('"')
              .append(MALCodegenHelper.escapeJava(
                  ((MALExpressionModel.StringArgument) arg).getValue()))
              .append('"');
        } else if (arg instanceof MALExpressionModel.StringListArgument) {
            final List<String> vals =
                ((MALExpressionModel.StringListArgument) arg).getValues();
            sb.append("java.util.Arrays.asList(new String[]{");
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"')
                  .append(MALCodegenHelper.escapeJava(vals.get(i)))
                  .append('"');
            }
            sb.append("})");
        } else if (arg instanceof MALExpressionModel.NumberListArgument) {
            final List<Double> vals =
                ((MALExpressionModel.NumberListArgument) arg).getValues();
            sb.append("java.util.Arrays.asList(new Number[]{");
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                final double v = vals.get(i);
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    sb.append("Integer.valueOf(").append((int) v).append(')');
                } else {
                    sb.append("Double.valueOf(").append(v).append(')');
                }
            }
            sb.append("})");
        } else if (arg instanceof MALExpressionModel.BoolArgument) {
            sb.append(((MALExpressionModel.BoolArgument) arg).isValue());
        } else if (arg instanceof MALExpressionModel.NullArgument) {
            sb.append("null");
        } else if (arg instanceof MALExpressionModel.EnumRefArgument) {
            final MALExpressionModel.EnumRefArgument enumRef =
                (MALExpressionModel.EnumRefArgument) arg;
            final String fqcn =
                MALCodegenHelper.ENUM_FQCN.get(enumRef.getEnumType());
            if (fqcn != null) {
                sb.append(fqcn);
            } else {
                sb.append(enumRef.getEnumType());
            }
            sb.append('.').append(enumRef.getEnumValue());
        } else if (arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                MALCodegenHelper.emitNumberValueOf(sb,
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue());
            } else if (innerExpr instanceof MALExpressionModel.MetricExpr
                    && ((MALExpressionModel.MetricExpr) innerExpr)
                        .getMethodChain().isEmpty()) {
                final String name =
                    ((MALExpressionModel.MetricExpr) innerExpr)
                        .getMetricName();
                if (MALCodegenHelper.isDownsamplingType(name)) {
                    sb.append(
                        MALCodegenHelper.ENUM_FQCN.get("DownsamplingType"))
                      .append('.').append(name);
                } else {
                    // Bare metric reference — emit inline lookup
                    sb.append("((").append(MALCodegenHelper.SF)
                      .append(") samples.getOrDefault(\"")
                      .append(MALCodegenHelper.escapeJava(name))
                      .append("\", ").append(MALCodegenHelper.SF)
                      .append(".EMPTY))");
                }
            } else {
                // Complex expression — use pre-computed temp variable
                final String temp = preComputedArgs != null
                    ? preComputedArgs.poll() : null;
                if (temp != null) {
                    sb.append(temp);
                } else {
                    throw new IllegalStateException(
                        "Complex expression argument not pre-computed");
                }
            }
        } else if (arg instanceof MALExpressionModel.ClosureArgument) {
            sb.append(exprCodegen.nextClosureFieldName());
        }
    }

    // ==================== Pre-computation ====================

    /**
     * Scans arguments for complex expressions and pre-computes them to temp variables.
     * Returns a queue of temp names consumed by {@link #generateArgument} in order.
     */
    private java.util.Queue<String> preComputeComplexArgs(
            final StringBuilder sb,
            final List<MALExpressionModel.Argument> args) {
        final java.util.Queue<String> temps = new java.util.LinkedList<>();
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.ExprArgument)) {
                continue;
            }
            final MALExpressionModel.Expr expr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (isSimpleExprArg(expr)) {
                continue;
            }
            temps.add(exprCodegen.emitExpr(sb, expr));
        }
        return temps.isEmpty() ? null : temps;
    }

    private static boolean isSimpleExprArg(
            final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            return true;
        }
        if (expr instanceof MALExpressionModel.MetricExpr) {
            return ((MALExpressionModel.MetricExpr) expr)
                .getMethodChain().isEmpty();
        }
        return false;
    }

    static boolean allStringArgs(
            final List<MALExpressionModel.Argument> args) {
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.StringArgument)
                    && !(arg instanceof MALExpressionModel.NullArgument)) {
                return false;
            }
        }
        return true;
    }
}
