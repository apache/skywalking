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
 * Generates Java source for method chain calls on {@code SampleFamily}.
 *
 * <p>Handles two kinds of method calls in the chain:
 * <ul>
 *   <li><b>Built-in methods</b>: {@code .sum(['tag'])}, {@code .rate("PT1M")}, {@code .tag({...})}
 *       — generates direct calls on the SampleFamily variable:
 *       {@code sf = sf.sum(Arrays.asList(new String[]{"tag"}));}</li>
 *   <li><b>Extension methods</b>: {@code .genai::estimateCost()}, {@code .test::scale(2.0)}
 *       — generates direct static method calls via SPI registry:
 *       {@code sf = TestMalExtension.scale(sf, 2.0);}</li>
 * </ul>
 *
 * <p>Also handles all argument codegen: strings, numbers, string lists, number lists,
 * enums, booleans, closures, and typed arguments for extension functions.
 */
final class MALMethodChainCodegen {

    private final MALExprCodegen exprCodegen;

    MALMethodChainCodegen(final MALExprCodegen exprCodegen) {
        this.exprCodegen = exprCodegen;
    }

    // ==================== Statement-based chain codegen ====================

    /**
     * Emits each method chain call as a reassignment of {@code sf}.
     *
     * <p>For {@code metric.sum(['svc']).rate("PT1M")}, generates:
     * <pre>
     *   sf = sf.sum(Arrays.asList(new String[]{"svc"}));
     *   sf = sf.rate("PT1M");
     * </pre>
     *
     * <p>For extension calls like {@code .test::scale(2.0)}, generates:
     * <pre>
     *   sf = TestMalExtension.scale(sf, 2.0);
     * </pre>
     */
    void emitChainStatements(final StringBuilder sb,
                              final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if (mc.isExtension()) {
                emitExtensionCall(sb, mc);
                continue;
            }
            sb.append("  ").append(MALCodegenHelper.RUN_VAR).append(" = ")
              .append(MALCodegenHelper.RUN_VAR).append('.').append(mc.getName()).append('(');
            final List<MALExpressionModel.Argument> args = mc.getArguments();
            if (MALCodegenHelper.VARARGS_STRING_METHODS.contains(mc.getName())
                    && !args.isEmpty() && allStringArgs(args)) {
                sb.append("new String[]{");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
                sb.append('}');
            } else {
                final boolean primitiveDouble =
                    MALCodegenHelper.PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateMethodCallArg(sb, args.get(i), primitiveDouble);
                }
            }
            sb.append(");\n");
        }
    }

    // ==================== Inline chain codegen ====================

    /**
     * Emits method chain inline (for sub-expressions in binary ops and function call args).
     *
     * <p>For {@code metric.sum(['svc'])}, emits: {@code .sum(Arrays.asList(new String[]{"svc"}))}
     */
    void emitMethodChainInline(final StringBuilder sb,
                                final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if (mc.isExtension()) {
                throw new IllegalStateException(
                    "Inline extension method calls are not supported: "
                        + mc.getNamespace() + "::" + mc.getName());
            }
            sb.append('.').append(mc.getName()).append('(');
            final List<MALExpressionModel.Argument> args = mc.getArguments();
            if (MALCodegenHelper.VARARGS_STRING_METHODS.contains(mc.getName())
                    && !args.isEmpty() && allStringArgs(args)) {
                sb.append("new String[]{");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
                sb.append('}');
            } else {
                final boolean primitiveDouble =
                    MALCodegenHelper.PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateMethodCallArg(sb, args.get(i), primitiveDouble);
                }
            }
            sb.append(')');
        }
    }

    // ==================== Extension function codegen ====================

    /**
     * Emits a direct static method call for an extension function.
     *
     * <p>For {@code .test::scale(3.0)}, generates:
     * {@code sf = org.apache...TestMalExtension.scale(sf, 3.0);}
     *
     * <p>Validates at compile time: namespace exists, method exists, arg count matches.
     */
    private void emitExtensionCall(final StringBuilder sb,
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
        sb.append("  ").append(MALCodegenHelper.RUN_VAR).append(" = ")
          .append(em.getDeclaringClass()).append('.')
          .append(em.getMethodName()).append('(')
          .append(MALCodegenHelper.RUN_VAR);
        for (int i = 0; i < args.size(); i++) {
            sb.append(", ");
            generateExtensionArg(sb, args.get(i), em.getExtraParamTypes()[i]);
        }
        sb.append(");\n");
    }

    /**
     * Generates a typed argument for an extension function call.
     *
     * <p>Supported types: {@code String}, {@code double}, {@code float},
     * {@code long}, {@code int}, {@code List<String>}.
     * Emits raw literals with appropriate suffixes (e.g. {@code 3.0}, {@code 10L}, {@code 3.0F}).
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
            final double raw = ((MALExpressionModel.NumberExpr) expr).getValue();
            if (expectedType == double.class || expectedType == Double.class) {
                sb.append(raw);
            } else if (expectedType == float.class || expectedType == Float.class) {
                sb.append((float) raw).append('F');
            } else if (expectedType == long.class || expectedType == Long.class) {
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
                "Unsupported extension parameter type: " + expectedType.getName());
        }
    }

    // ==================== Argument codegen ====================

    /**
     * Generates a method call argument with special handling for primitive double methods.
     *
     * <p>For {@code .valueEqual(33)}, emits raw double: {@code 33.0}
     * <p>For {@code .multiply(100)}, emits boxed: {@code Long.valueOf(100L)}
     */
    private void generateMethodCallArg(final StringBuilder sb,
                                        final MALExpressionModel.Argument arg,
                                        final boolean primitiveDouble) {
        if (primitiveDouble
                && arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                final double num =
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                sb.append(num);
                return;
            }
        }
        generateArgument(sb, arg);
    }

    /**
     * Generates a general argument (string, string list, number list, bool, null, enum,
     * expression, or closure reference).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "PT1M"} → {@code "PT1M"}</li>
     *   <li>{@code ['svc', 'inst']} → {@code Arrays.asList(new String[]{"svc","inst"})}</li>
     *   <li>{@code [50, 75, 90]} → {@code Arrays.asList(new Number[]{Integer.valueOf(50),...})}</li>
     *   <li>{@code Layer.GENERAL} → {@code org.apache...Layer.GENERAL}</li>
     *   <li>{@code {tags -> ...}} → {@code _tag} (static field reference)</li>
     *   <li>{@code 100} → {@code Long.valueOf(100L)}</li>
     * </ul>
     */
    void generateArgument(final StringBuilder sb,
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
                  .append(MALCodegenHelper.escapeJava(vals.get(i))).append('"');
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
                final double num =
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                MALCodegenHelper.emitNumberValueOf(sb, num);
            } else if (innerExpr instanceof MALExpressionModel.MetricExpr
                    && ((MALExpressionModel.MetricExpr) innerExpr)
                        .getMethodChain().isEmpty()) {
                final String name =
                    ((MALExpressionModel.MetricExpr) innerExpr).getMetricName();
                if (MALCodegenHelper.isDownsamplingType(name)) {
                    sb.append(MALCodegenHelper.ENUM_FQCN.get("DownsamplingType"))
                      .append('.').append(name);
                } else {
                    exprCodegen.generateExprInline(sb, innerExpr);
                }
            } else {
                exprCodegen.generateExprInline(sb, innerExpr);
            }
        } else if (arg instanceof MALExpressionModel.ClosureArgument) {
            sb.append(exprCodegen.nextClosureFieldName());
        }
    }

    static boolean allStringArgs(final List<MALExpressionModel.Argument> args) {
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.StringArgument)
                    && !(arg instanceof MALExpressionModel.NullArgument)) {
                return false;
            }
        }
        return true;
    }
}
