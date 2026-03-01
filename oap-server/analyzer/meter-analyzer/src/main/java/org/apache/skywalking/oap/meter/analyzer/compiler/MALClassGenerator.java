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

package org.apache.skywalking.oap.meter.analyzer.compiler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.compiler.rt.MalExpressionPackageHolder;
import org.apache.skywalking.oap.meter.analyzer.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.dsl.MalFilter;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

/**
 * Generates {@link MalExpression} implementation classes from
 * {@link MALExpressionModel} AST using Javassist bytecode generation.
 *
 * <p>Each generated class implements:
 * <pre>
 *   SampleFamily run(Map&lt;String, SampleFamily&gt; samples)
 * </pre>
 */
@Slf4j
public final class MALClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.meter.analyzer.compiler.rt.";

    private static final String SF = "org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily";

    private static final String ENUM_PACKAGE_PREFIX =
        "org.apache.skywalking.oap.";

    /**
     * Well-known enum types used in MAL expressions.
     */
    private static final java.util.Map<String, String> ENUM_FQCN;

    static {
        ENUM_FQCN = new java.util.HashMap<>();
        ENUM_FQCN.put("Layer", "org.apache.skywalking.oap.server.core.analysis.Layer");
        ENUM_FQCN.put("DetectPoint", "org.apache.skywalking.oap.server.core.source.DetectPoint");
        ENUM_FQCN.put("K8sRetagType",
            "org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.K8sRetagType");
        ENUM_FQCN.put("DownsamplingType",
            "org.apache.skywalking.oap.meter.analyzer.dsl.DownsamplingType");
    }

    private final ClassPool classPool;
    private int closureCounter;

    public MALClassGenerator() {
        this(ClassPool.getDefault());
    }

    public MALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
    }

    /**
     * Compiles a MAL expression into a MalExpression implementation.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param expression the MAL expression string
     * @return a MalExpression instance
     */
    public MalExpression compile(final String metricName,
                                 final String expression) throws Exception {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        return compileFromModel(metricName, ast);
    }

    /**
     * Compiles a MAL filter closure into a {@link MalFilter} implementation.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     * @return a MalFilter instance
     */
    @SuppressWarnings("unchecked")
    public MalFilter compileFilter(final String filterExpression) throws Exception {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final String className = PACKAGE_PREFIX + "MalFilter_"
            + CLASS_COUNTER.getAndIncrement();

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.dsl.MalFilter"));

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final StringBuilder sb = new StringBuilder();
        sb.append("public boolean test(java.util.Map ").append(paramName)
          .append(") {\n");

        final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
        if (body.size() == 1
                && body.get(0) instanceof MALExpressionModel.ClosureExprStatement) {
            // Single expression → evaluate as condition and return boolean
            final MALExpressionModel.ClosureExpr expr =
                ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
            if (expr instanceof MALExpressionModel.ClosureCondition) {
                sb.append("  return ");
                generateClosureCondition(
                    sb, (MALExpressionModel.ClosureCondition) expr, paramName);
                sb.append(";\n");
            } else {
                // Truthy evaluation of the expression
                sb.append("  Object _v = ");
                generateClosureExpr(sb, expr, paramName);
                sb.append(";\n");
                sb.append("  return _v != null && !Boolean.FALSE.equals(_v);\n");
            }
        } else {
            // Multi-statement body — generate statements, last expression is the return
            for (final MALExpressionModel.ClosureStatement stmt : body) {
                generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return false;\n");
        }
        sb.append("}\n");

        final String filterBody = sb.toString();
        if (log.isDebugEnabled()) {
            log.debug("MAL compileFilter AST: {}", closure);
            log.debug("MAL compileFilter test():\n{}", filterBody);
        }

        ctClass.addMethod(CtNewMethod.make(filterBody, ctClass));

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        return (MalFilter) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Compiles from a pre-parsed AST model.
     */
    public MalExpression compileFromModel(final String metricName,
                                          final MALExpressionModel.Expr ast) throws Exception {
        final String className = PACKAGE_PREFIX + "MalExpr_"
            + CLASS_COUNTER.getAndIncrement();

        closureCounter = 0;
        final List<ClosureInfo> closures = new ArrayList<>();
        collectClosures(ast, closures);

        // Generate closure classes first
        final List<Object> closureInstances = new ArrayList<>();
        for (int i = 0; i < closures.size(); i++) {
            final String closureName = className + "_Closure" + i;
            final Object instance = compileClosureClass(
                closureName, closures.get(i));
            closureInstances.add(instance);
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.dsl.MalExpression"));

        // Add closure fields
        for (int i = 0; i < closures.size(); i++) {
            final String fieldType = closures.get(i).interfaceType;
            ctClass.addField(javassist.CtField.make(
                "public " + fieldType + " _closure" + i + ";", ctClass));
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final String runBody = generateRunMethod(ast);
        final ExpressionMetadata metadata = extractMetadata(ast);
        final String metadataBody = generateMetadataMethod(metadata);

        if (log.isDebugEnabled()) {
            log.debug("MAL compile [{}] AST: {}", metricName, ast);
            log.debug("MAL compile [{}] run():\n{}", metricName, runBody);
            log.debug("MAL compile [{}] metadata():\n{}", metricName, metadataBody);
        }

        ctClass.addMethod(CtNewMethod.make(runBody, ctClass));
        ctClass.addMethod(CtNewMethod.make(metadataBody, ctClass));

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        final MalExpression instance = (MalExpression) clazz.getDeclaredConstructor()
            .newInstance();

        // Set closure fields via reflection
        for (int i = 0; i < closureInstances.size(); i++) {
            final java.lang.reflect.Field field =
                clazz.getField("_closure" + i);
            field.set(instance, closureInstances.get(i));
        }

        return instance;
    }

    private static final class ClosureInfo {
        final MALExpressionModel.ClosureArgument closure;
        final String interfaceType;
        int fieldIndex;

        ClosureInfo(final MALExpressionModel.ClosureArgument closure,
                    final String interfaceType) {
            this.closure = closure;
            this.interfaceType = interfaceType;
        }
    }

    private void collectClosures(final MALExpressionModel.Expr expr,
                                 final List<ClosureInfo> closures) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            collectClosuresFromChain(
                ((MALExpressionModel.MetricExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getLeft(), closures);
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getRight(), closures);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectClosures(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), closures);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectClosures(
                ((MALExpressionModel.ParenChainExpr) expr).getInner(), closures);
            collectClosuresFromChain(
                ((MALExpressionModel.ParenChainExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            collectClosuresFromArgs(
                ((MALExpressionModel.FunctionCallExpr) expr).getArguments(), closures);
            collectClosuresFromChain(
                ((MALExpressionModel.FunctionCallExpr) expr).getMethodChain(), closures);
        }
    }

    private void collectClosuresFromChain(final List<MALExpressionModel.MethodCall> chain,
                                          final List<ClosureInfo> closures) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            collectClosuresFromArgs(mc.getArguments(), closures);
        }
    }

    private void collectClosuresFromArgs(final List<MALExpressionModel.Argument> args,
                                         final List<ClosureInfo> closures) {
        for (final MALExpressionModel.Argument arg : args) {
            if (arg instanceof MALExpressionModel.ClosureArgument) {
                final ClosureInfo info = new ClosureInfo(
                    (MALExpressionModel.ClosureArgument) arg,
                    "org.apache.skywalking.oap.meter.analyzer.dsl"
                    + ".SampleFamilyFunctions$TagFunction");
                info.fieldIndex = closures.size();
                closures.add(info);
            } else if (arg instanceof MALExpressionModel.ExprArgument) {
                collectClosures(
                    ((MALExpressionModel.ExprArgument) arg).getExpr(), closures);
            }
        }
    }

    private Object compileClosureClass(final String className,
                                       final ClosureInfo info) throws Exception {
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(info.interfaceType));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final MALExpressionModel.ClosureArgument closure = info.closure;
        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final StringBuilder sb = new StringBuilder();
        sb.append("public java.util.Map apply(java.util.Map ").append(paramName)
          .append(") {\n");
        for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
            generateClosureStatement(sb, stmt, paramName);
        }
        sb.append("  return ").append(paramName).append(";\n");
        sb.append("}\n");

        // Also add the Object apply(Object) bridge method
        ctClass.addMethod(CtNewMethod.make(sb.toString(), ctClass));
        ctClass.addMethod(CtNewMethod.make(
            "public Object apply(Object o) { return apply((java.util.Map) o); }",
            ctClass));

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        return clazz.getDeclaredConstructor().newInstance();
    }

    private String generateRunMethod(final MALExpressionModel.Expr ast) {
        final StringBuilder sb = new StringBuilder();
        sb.append("public ").append(SF).append(" run(java.util.Map samples) {\n");
        sb.append("  return ");
        generateExpr(sb, ast);
        sb.append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private void generateExpr(final StringBuilder sb,
                              final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            generateMetricExpr(sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            final double val = ((MALExpressionModel.NumberExpr) expr).getValue();
            sb.append(SF).append(".EMPTY.plus(Double.valueOf(").append(val).append("))");
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            generateBinaryExpr(sb, (MALExpressionModel.BinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            sb.append("(");
            generateExpr(sb, ((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            sb.append(").negative()");
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            generateFunctionCallExpr(sb, (MALExpressionModel.FunctionCallExpr) expr);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            generateParenChainExpr(sb, (MALExpressionModel.ParenChainExpr) expr);
        }
    }

    private void generateMetricExpr(final StringBuilder sb,
                                    final MALExpressionModel.MetricExpr expr) {
        sb.append("((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY))");
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateFunctionCallExpr(final StringBuilder sb,
                                          final MALExpressionModel.FunctionCallExpr expr) {
        // Top-level functions like count(metric), topN(metric, n, Order)
        // These are static-style calls on the first argument (SampleFamily)
        final String fn = expr.getFunctionName();
        final List<MALExpressionModel.Argument> args = expr.getArguments();

        if (("count".equals(fn) || "topN".equals(fn)) && !args.isEmpty()) {
            // First arg is the SampleFamily
            final MALExpressionModel.Argument firstArg = args.get(0);
            if (firstArg instanceof MALExpressionModel.ExprArgument) {
                generateExpr(sb,
                    ((MALExpressionModel.ExprArgument) firstArg).getExpr());
            }
            sb.append('.').append(fn).append('(');
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(')');
        } else {
            // Generic function call
            sb.append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(')');
        }
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateParenChainExpr(final StringBuilder sb,
                                        final MALExpressionModel.ParenChainExpr expr) {
        sb.append("(");
        generateExpr(sb, expr.getInner());
        sb.append(")");
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateBinaryExpr(final StringBuilder sb,
                                    final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();

        final boolean leftIsNumber = left instanceof MALExpressionModel.NumberExpr;
        final boolean rightIsNumber = right instanceof MALExpressionModel.NumberExpr;

        if (leftIsNumber && !rightIsNumber) {
            // N op SF -> swap to SF.op(N) with special handling for SUB and DIV
            final double num = ((MALExpressionModel.NumberExpr) left).getValue();
            switch (op) {
                case ADD:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").plus(Double.valueOf(").append(num).append("))");
                    break;
                case SUB:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").minus(Double.valueOf(")
                      .append(num).append(")).negative()");
                    break;
                case MUL:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").multiply(Double.valueOf(").append(num).append("))");
                    break;
                case DIV:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").newValue(new java.util.function.Function() { ")
                      .append("public Object apply(Object v) { ")
                      .append("return Double.valueOf(").append(num)
                      .append(" / ((Double)v).doubleValue()); } })");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
        } else if (!leftIsNumber && rightIsNumber) {
            // SF op N
            final double num = ((MALExpressionModel.NumberExpr) right).getValue();
            sb.append("(");
            generateExpr(sb, left);
            sb.append(").").append(opMethodName(op))
              .append("(Double.valueOf(").append(num).append("))");
        } else {
            // SF op SF (both non-number)
            sb.append("(");
            generateExpr(sb, left);
            sb.append(").").append(opMethodName(op)).append("(");
            generateExpr(sb, right);
            sb.append(")");
        }
    }

    /**
     * Methods on SampleFamily that take String[] (varargs).
     * Javassist doesn't support varargs syntax, so multiple string args
     * must be wrapped in {@code new String[]{}}.
     */
    private static final Set<String> VARARGS_STRING_METHODS = Set.of(
        "tagEqual", "tagNotEqual", "tagMatch", "tagNotMatch"
    );

    private void generateMethodChain(final StringBuilder sb,
                                     final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            sb.append('.').append(mc.getName()).append('(');
            final List<MALExpressionModel.Argument> args = mc.getArguments();
            if (VARARGS_STRING_METHODS.contains(mc.getName()) && !args.isEmpty()
                    && allStringArgs(args)) {
                sb.append("new String[]{");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
                sb.append('}');
            } else {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
            }
            sb.append(')');
        }
    }

    private static boolean allStringArgs(final List<MALExpressionModel.Argument> args) {
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.StringArgument)) {
                return false;
            }
        }
        return true;
    }

    private void generateArgument(final StringBuilder sb,
                                  final MALExpressionModel.Argument arg) {
        if (arg instanceof MALExpressionModel.StringArgument) {
            sb.append('"')
              .append(escapeJava(((MALExpressionModel.StringArgument) arg).getValue()))
              .append('"');
        } else if (arg instanceof MALExpressionModel.StringListArgument) {
            final List<String> vals =
                ((MALExpressionModel.StringListArgument) arg).getValues();
            sb.append("java.util.List.of(");
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(escapeJava(vals.get(i))).append('"');
            }
            sb.append(')');
        } else if (arg instanceof MALExpressionModel.NumberListArgument) {
            final List<Double> vals =
                ((MALExpressionModel.NumberListArgument) arg).getValues();
            sb.append("java.util.List.of(");
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
            sb.append(')');
        } else if (arg instanceof MALExpressionModel.BoolArgument) {
            sb.append(((MALExpressionModel.BoolArgument) arg).isValue());
        } else if (arg instanceof MALExpressionModel.EnumRefArgument) {
            final MALExpressionModel.EnumRefArgument enumRef =
                (MALExpressionModel.EnumRefArgument) arg;
            final String fqcn = ENUM_FQCN.get(enumRef.getEnumType());
            if (fqcn != null) {
                sb.append(fqcn);
            } else {
                sb.append(enumRef.getEnumType());
            }
            sb.append('.').append(enumRef.getEnumValue());
        } else if (arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.MetricExpr
                    && ((MALExpressionModel.MetricExpr) innerExpr).getMethodChain().isEmpty()) {
                // Bare identifier — could be an enum constant like SUM, AVG
                final String name =
                    ((MALExpressionModel.MetricExpr) innerExpr).getMetricName();
                if (isDownsamplingType(name)) {
                    sb.append(ENUM_FQCN.get("DownsamplingType")).append('.').append(name);
                } else {
                    // It's a metric reference used as argument (e.g., div(other_metric))
                    generateExpr(sb, innerExpr);
                }
            } else {
                generateExpr(sb, innerExpr);
            }
        } else if (arg instanceof MALExpressionModel.ClosureArgument) {
            generateClosureArgument(sb, (MALExpressionModel.ClosureArgument) arg);
        }
    }

    private void generateClosureArgument(final StringBuilder sb,
                                         final MALExpressionModel.ClosureArgument closure) {
        // Reference pre-compiled closure field
        sb.append("this._closure").append(closureCounter++);
    }

    private void generateClosureStatement(final StringBuilder sb,
                                          final MALExpressionModel.ClosureStatement stmt,
                                          final String paramName) {
        if (stmt instanceof MALExpressionModel.ClosureAssignment) {
            final MALExpressionModel.ClosureAssignment assign =
                (MALExpressionModel.ClosureAssignment) stmt;
            sb.append("    ").append(paramName).append(".put(\"")
              .append(escapeJava(assign.getTarget())).append("\", ");
            generateClosureExpr(sb, assign.getValue(), paramName);
            sb.append(");\n");
        } else if (stmt instanceof MALExpressionModel.ClosureIfStatement) {
            final MALExpressionModel.ClosureIfStatement ifStmt =
                (MALExpressionModel.ClosureIfStatement) stmt;
            sb.append("    if (");
            generateClosureCondition(sb, ifStmt.getCondition(), paramName);
            sb.append(") {\n");
            for (final MALExpressionModel.ClosureStatement s : ifStmt.getThenBranch()) {
                generateClosureStatement(sb, s, paramName);
            }
            sb.append("    }\n");
            if (!ifStmt.getElseBranch().isEmpty()) {
                sb.append("    else {\n");
                for (final MALExpressionModel.ClosureStatement s : ifStmt.getElseBranch()) {
                    generateClosureStatement(sb, s, paramName);
                }
                sb.append("    }\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureReturnStatement) {
            sb.append("    return (java.util.Map) ");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureReturnStatement) stmt).getValue(), paramName);
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureExprStatement) {
            sb.append("    ");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprStatement) stmt).getExpr(), paramName);
            sb.append(";\n");
        }
    }

    private void generateClosureExpr(final StringBuilder sb,
                                     final MALExpressionModel.ClosureExpr expr,
                                     final String paramName) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            sb.append('"')
              .append(escapeJava(((MALExpressionModel.ClosureStringLiteral) expr).getValue()))
              .append('"');
        } else if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            sb.append(((MALExpressionModel.ClosureNumberLiteral) expr).getValue());
        } else if (expr instanceof MALExpressionModel.ClosureBoolLiteral) {
            sb.append(((MALExpressionModel.ClosureBoolLiteral) expr).isValue());
        } else if (expr instanceof MALExpressionModel.ClosureNullLiteral) {
            sb.append("null");
        } else if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            generateClosureMethodChain(sb,
                (MALExpressionModel.ClosureMethodChain) expr, paramName);
        } else if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            final MALExpressionModel.ClosureBinaryExpr bin =
                (MALExpressionModel.ClosureBinaryExpr) expr;
            sb.append("(");
            generateClosureExpr(sb, bin.getLeft(), paramName);
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
                    sb.append(" / ");
                    break;
                default:
                    break;
            }
            generateClosureExpr(sb, bin.getRight(), paramName);
            sb.append(")");
        }
    }

    private void generateClosureMethodChain(
            final StringBuilder sb,
            final MALExpressionModel.ClosureMethodChain chain,
            final String paramName) {
        // tags.key -> tags.get("key")
        // tags['key'] -> tags.get("key")
        final List<MALExpressionModel.ClosureChainSegment> segs = chain.getSegments();
        if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureFieldAccess) {
            final String key =
                ((MALExpressionModel.ClosureFieldAccess) segs.get(0)).getName();
            sb.append(chain.getTarget()).append(".get(\"")
              .append(escapeJava(key)).append("\")");
        } else if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureIndexAccess) {
            sb.append(chain.getTarget()).append(".get(");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureIndexAccess) segs.get(0)).getIndex(), paramName);
            sb.append(")");
        } else {
            // General chain: build in a local buffer to support safe navigation
            final StringBuilder local = new StringBuilder();
            local.append(chain.getTarget());
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append(".get(\"")
                      .append(escapeJava(
                          ((MALExpressionModel.ClosureFieldAccess) seg).getName()))
                      .append("\")");
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append(".get(");
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(), paramName);
                    local.append(")");
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    if (mc.isSafeNav()) {
                        final String prior = local.toString();
                        local.setLength(0);
                        local.append("(").append(prior).append(" == null ? null : ")
                          .append("((String) ").append(prior).append(").")
                          .append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName);
                        }
                        local.append("))");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
        }
    }

    private void generateClosureCondition(final StringBuilder sb,
                                          final MALExpressionModel.ClosureCondition cond,
                                          final String paramName) {
        if (cond instanceof MALExpressionModel.ClosureComparison) {
            final MALExpressionModel.ClosureComparison cc =
                (MALExpressionModel.ClosureComparison) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName);
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName);
                    sb.append(")");
                    break;
                default:
                    generateClosureExpr(sb, cc.getLeft(), paramName);
                    sb.append(comparisonOperator(cc.getOp()));
                    generateClosureExpr(sb, cc.getRight(), paramName);
                    break;
            }
        } else if (cond instanceof MALExpressionModel.ClosureLogical) {
            final MALExpressionModel.ClosureLogical lc =
                (MALExpressionModel.ClosureLogical) cond;
            sb.append("(");
            generateClosureCondition(sb, lc.getLeft(), paramName);
            sb.append(lc.getOp() == MALExpressionModel.LogicalOp.AND ? " && " : " || ");
            generateClosureCondition(sb, lc.getRight(), paramName);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureNot) {
            sb.append("!(");
            generateClosureCondition(sb,
                ((MALExpressionModel.ClosureNot) cond).getInner(), paramName);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureExprCondition) {
            // Truthiness check
            sb.append("(");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprCondition) cond).getExpr(), paramName);
            sb.append(" != null)");
        } else if (cond instanceof MALExpressionModel.ClosureInCondition) {
            final MALExpressionModel.ClosureInCondition ic =
                (MALExpressionModel.ClosureInCondition) cond;
            sb.append("java.util.List.of(");
            for (int i = 0; i < ic.getValues().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(escapeJava(ic.getValues().get(i))).append('"');
            }
            sb.append(").contains(");
            generateClosureExpr(sb, ic.getExpr(), paramName);
            sb.append(")");
        }
    }

    private static void collectSampleNames(final MALExpressionModel.Expr expr,
                                            final Set<String> names) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            final MALExpressionModel.MetricExpr me = (MALExpressionModel.MetricExpr) expr;
            names.add(me.getMetricName());
            collectSampleNamesFromChain(me.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectSampleNames(((MALExpressionModel.BinaryExpr) expr).getLeft(), names);
            collectSampleNames(((MALExpressionModel.BinaryExpr) expr).getRight(), names);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectSampleNames(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), names);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            final MALExpressionModel.ParenChainExpr pce =
                (MALExpressionModel.ParenChainExpr) expr;
            collectSampleNames(pce.getInner(), names);
            collectSampleNamesFromChain(pce.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    private static void collectSampleNamesFromChain(
            final List<MALExpressionModel.MethodCall> chain,
            final Set<String> names) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if ("downsampling".equals(mc.getName())) {
                continue;
            }
            for (final MALExpressionModel.Argument arg : mc.getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    /**
     * Extracts compile-time metadata from the AST by walking all method chains.
     */
    static ExpressionMetadata extractMetadata(final MALExpressionModel.Expr ast) {
        final Set<String> sampleNames = new LinkedHashSet<>();
        collectSampleNames(ast, sampleNames);

        ScopeType scopeType = null;
        final Set<String> scopeLabels = new LinkedHashSet<>();
        final Set<String> aggregationLabels = new LinkedHashSet<>();
        DownsamplingType downsampling = DownsamplingType.AVG;
        boolean isHistogram = false;
        int[] percentiles = null;

        final List<List<MALExpressionModel.MethodCall>> allChains = new ArrayList<>();
        collectMethodChains(ast, allChains);

        for (final List<MALExpressionModel.MethodCall> chain : allChains) {
            for (final MALExpressionModel.MethodCall mc : chain) {
                final String name = mc.getName();
                switch (name) {
                    case "sum":
                    case "avg":
                    case "max":
                    case "min":
                        addStringListLabels(mc, aggregationLabels);
                        break;
                    case "count":
                        addStringListLabels(mc, aggregationLabels);
                        break;
                    case "service":
                        scopeType = ScopeType.SERVICE;
                        addStringListLabels(mc, scopeLabels);
                        break;
                    case "instance":
                        scopeType = ScopeType.SERVICE_INSTANCE;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "endpoint":
                        scopeType = ScopeType.ENDPOINT;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "process":
                        scopeType = ScopeType.PROCESS;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "serviceRelation":
                        scopeType = ScopeType.SERVICE_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "processRelation":
                        scopeType = ScopeType.PROCESS_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "histogram":
                        isHistogram = true;
                        break;
                    case "histogram_percentile":
                        if (!mc.getArguments().isEmpty()
                                && mc.getArguments().get(0) instanceof MALExpressionModel.NumberListArgument) {
                            final List<Double> vals =
                                ((MALExpressionModel.NumberListArgument) mc.getArguments().get(0)).getValues();
                            percentiles = new int[vals.size()];
                            for (int i = 0; i < vals.size(); i++) {
                                percentiles[i] = vals.get(i).intValue();
                            }
                        }
                        break;
                    case "downsampling":
                        if (!mc.getArguments().isEmpty()) {
                            final MALExpressionModel.Argument dsArg = mc.getArguments().get(0);
                            if (dsArg instanceof MALExpressionModel.EnumRefArgument) {
                                final String val =
                                    ((MALExpressionModel.EnumRefArgument) dsArg).getEnumValue();
                                downsampling = DownsamplingType.valueOf(val);
                            } else if (dsArg instanceof MALExpressionModel.ExprArgument) {
                                final MALExpressionModel.Expr dsExpr =
                                    ((MALExpressionModel.ExprArgument) dsArg).getExpr();
                                if (dsExpr instanceof MALExpressionModel.MetricExpr) {
                                    final String val =
                                        ((MALExpressionModel.MetricExpr) dsExpr).getMetricName();
                                    downsampling = DownsamplingType.valueOf(val);
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        return new ExpressionMetadata(
            new ArrayList<>(sampleNames),
            scopeType,
            scopeLabels,
            aggregationLabels,
            downsampling,
            isHistogram,
            percentiles
        );
    }

    private static void addStringListLabels(final MALExpressionModel.MethodCall mc,
                                             final Set<String> target) {
        if (!mc.getArguments().isEmpty()
                && mc.getArguments().get(0) instanceof MALExpressionModel.StringListArgument) {
            target.addAll(
                ((MALExpressionModel.StringListArgument) mc.getArguments().get(0)).getValues());
        }
    }

    private static void addAllStringListLabels(final MALExpressionModel.MethodCall mc,
                                                final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringListArgument) {
                target.addAll(((MALExpressionModel.StringListArgument) arg).getValues());
            }
        }
    }

    private static void addStringArgLabels(final MALExpressionModel.MethodCall mc,
                                            final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringArgument) {
                target.add(((MALExpressionModel.StringArgument) arg).getValue());
            }
        }
    }

    private static void collectMethodChains(final MALExpressionModel.Expr expr,
                                             final List<List<MALExpressionModel.MethodCall>> chains) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            chains.add(((MALExpressionModel.MetricExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectMethodChains(((MALExpressionModel.BinaryExpr) expr).getLeft(), chains);
            collectMethodChains(((MALExpressionModel.BinaryExpr) expr).getRight(), chains);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectMethodChains(((MALExpressionModel.UnaryNegExpr) expr).getOperand(), chains);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectMethodChains(((MALExpressionModel.ParenChainExpr) expr).getInner(), chains);
            chains.add(((MALExpressionModel.ParenChainExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectMethodChains(((MALExpressionModel.ExprArgument) arg).getExpr(), chains);
                }
            }
            chains.add(((MALExpressionModel.FunctionCallExpr) expr).getMethodChain());
        }
    }

    private String generateMetadataMethod(final ExpressionMetadata metadata) {
        final StringBuilder sb = new StringBuilder();
        final String mdClass = "org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionMetadata";
        final String scopeTypeClass = "org.apache.skywalking.oap.server.core.analysis.meter.ScopeType";
        final String dsTypeClass = "org.apache.skywalking.oap.meter.analyzer.dsl.DownsamplingType";

        sb.append("public ").append(mdClass).append(" metadata() {\n");

        // samples list
        sb.append("  java.util.List _samples = new java.util.ArrayList();\n");
        for (final String sample : metadata.getSamples()) {
            sb.append("  _samples.add(\"").append(escapeJava(sample)).append("\");\n");
        }

        // scope labels set
        sb.append("  java.util.Set _scopeLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getScopeLabels()) {
            sb.append("  _scopeLabels.add(\"").append(escapeJava(label)).append("\");\n");
        }

        // aggregation labels set
        sb.append("  java.util.Set _aggLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getAggregationLabels()) {
            sb.append("  _aggLabels.add(\"").append(escapeJava(label)).append("\");\n");
        }

        // percentiles array
        if (metadata.getPercentiles() != null) {
            sb.append("  int[] _pct = new int[]{");
            for (int i = 0; i < metadata.getPercentiles().length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(metadata.getPercentiles()[i]);
            }
            sb.append("};\n");
        } else {
            sb.append("  int[] _pct = null;\n");
        }

        sb.append("  return new ").append(mdClass).append("(\n");
        sb.append("    _samples,\n");
        if (metadata.getScopeType() != null) {
            sb.append("    ").append(scopeTypeClass).append('.').append(metadata.getScopeType().name()).append(",\n");
        } else {
            sb.append("    null,\n");
        }
        sb.append("    _scopeLabels,\n");
        sb.append("    _aggLabels,\n");
        sb.append("    ").append(dsTypeClass).append('.').append(metadata.getDownsampling().name()).append(",\n");
        sb.append("    ").append(metadata.isHistogram()).append(",\n");
        sb.append("    _pct\n");
        sb.append("  );\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String opMethodName(final MALExpressionModel.ArithmeticOp op) {
        switch (op) {
            case ADD:
                return "plus";
            case SUB:
                return "minus";
            case MUL:
                return "multiply";
            case DIV:
                return "div";
            default:
                throw new IllegalArgumentException("Unknown op: " + op);
        }
    }

    private static String comparisonOperator(final MALExpressionModel.CompareOp op) {
        switch (op) {
            case GT:
                return " > ";
            case LT:
                return " < ";
            case GTE:
                return " >= ";
            case LTE:
                return " <= ";
            default:
                return " == ";
        }
    }

    private static boolean isDownsamplingType(final String name) {
        return "AVG".equals(name) || "SUM".equals(name) || "LATEST".equals(name)
            || "SUM_PER_MIN".equals(name) || "MAX".equals(name) || "MIN".equals(name);
    }

    private static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generates the Java source body of the run method for debugging/testing.
     */
    public String generateSource(final String expression) {
        closureCounter = 0;
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        return generateRunMethod(ast);
    }

    /**
     * Generates the Java source body of the filter test method for debugging/testing.
     */
    public String generateFilterSource(final String filterExpression) {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final StringBuilder sb = new StringBuilder();
        sb.append("public boolean test(java.util.Map ").append(paramName)
          .append(") {\n");

        final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
        if (body.size() == 1
                && body.get(0) instanceof MALExpressionModel.ClosureExprStatement) {
            final MALExpressionModel.ClosureExpr expr =
                ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
            if (expr instanceof MALExpressionModel.ClosureCondition) {
                sb.append("  return ");
                generateClosureCondition(
                    sb, (MALExpressionModel.ClosureCondition) expr, paramName);
                sb.append(";\n");
            } else {
                sb.append("  Object _v = ");
                generateClosureExpr(sb, expr, paramName);
                sb.append(";\n");
                sb.append("  return _v != null && !Boolean.FALSE.equals(_v);\n");
            }
        } else {
            for (final MALExpressionModel.ClosureStatement stmt : body) {
                generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return false;\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
