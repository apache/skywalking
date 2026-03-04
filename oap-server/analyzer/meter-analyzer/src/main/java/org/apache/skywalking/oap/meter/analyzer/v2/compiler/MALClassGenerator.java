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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
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
        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.";

    private static final String SF = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily";

    private static final String ENUM_PACKAGE_PREFIX =
        "org.apache.skywalking.oap.";

    /**
     * Well-known enum types used in MAL expressions.
     */
    private static final java.util.Map<String, String> ENUM_FQCN;

    /**
     * Well-known helper classes used inside MAL closures (Groovy imports).
     */
    private static final java.util.Map<String, String> CLOSURE_CLASS_FQCN;

    static {
        ENUM_FQCN = new java.util.HashMap<>();
        ENUM_FQCN.put("Layer", "org.apache.skywalking.oap.server.core.analysis.Layer");
        ENUM_FQCN.put("DetectPoint", "org.apache.skywalking.oap.server.core.source.DetectPoint");
        ENUM_FQCN.put("K8sRetagType",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.tagOpt.K8sRetagType");
        ENUM_FQCN.put("DownsamplingType",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType");

        CLOSURE_CLASS_FQCN = new java.util.HashMap<>();
        CLOSURE_CLASS_FQCN.put("ProcessRegistry",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry");
    }

    private static final Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private List<String> closureFieldNames;
    private int closureFieldIndex;
    private File classOutputDir;
    private String classNameHint;

    public MALClassGenerator() {
        this(createClassPool());
    }

    private static ClassPool createClassPool() {
        final ClassPool pool = new ClassPool(true);
        pool.appendClassPath(
            new javassist.LoaderClassPath(
                Thread.currentThread().getContextClassLoader()));
        return pool;
    }

    public MALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
    }

    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    public void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(PACKAGE_PREFIX + sanitizeName(classNameHint));
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    private String dedupClassName(final String base) {
        if (USED_CLASS_NAMES.add(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            final String candidate = base + "_" + i;
            if (USED_CLASS_NAMES.add(candidate)) {
                return candidate;
            }
        }
    }

    static String sanitizeName(final String name) {
        final StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            sb.append(i == 0
                ? (Character.isJavaIdentifierStart(c) ? c : '_')
                : (Character.isJavaIdentifierPart(c) ? c : '_'));
        }
        return sb.length() == 0 ? "Generated" : sb.toString();
    }

    private void writeClassFile(final CtClass ctClass) {
        if (classOutputDir == null) {
            return;
        }
        if (!classOutputDir.exists()) {
            classOutputDir.mkdirs();
        }
        final File file = new File(classOutputDir, ctClass.getSimpleName() + ".class");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            ctClass.toBytecode(out);
        } catch (Exception e) {
            log.warn("Failed to write class file {}: {}", file, e.getMessage());
        }
    }

    private void addLocalVariableTable(final javassist.CtMethod method,
                                       final String className,
                                       final String[][] vars) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final int len = code.getCodeLength();

            final javassist.bytecode.LocalVariableAttribute lva =
                new javassist.bytecode.LocalVariableAttribute(cp);
            lva.addEntry(0, len,
                cp.addUtf8Info("this"),
                cp.addUtf8Info("L" + className.replace('.', '/') + ";"), 0);
            for (int i = 0; i < vars.length; i++) {
                lva.addEntry(0, len,
                    cp.addUtf8Info(vars[i][0]),
                    cp.addUtf8Info(vars[i][1]), i + 1);
            }
            code.getAttributes().add(lva);
        } catch (Exception e) {
            log.warn("Failed to add LocalVariableTable: {}", e.getMessage());
        }
    }

    private void addRunLocalVariableTable(final javassist.CtMethod method,
                                          final String className,
                                          final int tempCount) {
        final String sfDesc = "L" + SF.replace('.', '/') + ";";
        final String[][] vars = new String[2 + tempCount][];
        vars[0] = new String[]{"samples", "Ljava/util/Map;"};
        vars[1] = new String[]{RUN_VAR, sfDesc};
        for (int i = 0; i < tempCount; i++) {
            vars[2 + i] = new String[]{"_t" + i, sfDesc};
        }
        addLocalVariableTable(method, className, vars);
    }

    /**
     * Compiles a MAL expression into a MalExpression implementation.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param expression the MAL expression string
     * @return a MalExpression instance
     * @throws Exception if parsing or compilation fails
     */
    public MalExpression compile(final String metricName,
                                 final String expression) throws Exception {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final String saved = classNameHint;
        if (classNameHint == null) {
            classNameHint = metricName;
        }
        try {
            return compileFromModel(metricName, ast);
        } finally {
            classNameHint = saved;
        }
    }

    /**
     * Compiles a MAL filter closure into a {@link MalFilter} implementation.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     * @return a MalFilter instance
     * @throws Exception if parsing or compilation fails
     */
    @SuppressWarnings("unchecked")
    public MalFilter compileFilter(final String filterExpression) throws Exception {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final String className = makeClassName("MalFilter_");

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter"));

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

        final javassist.CtMethod testMethod =
            CtNewMethod.make(filterBody, ctClass);
        ctClass.addMethod(testMethod);
        addLocalVariableTable(testMethod, className, new String[][]{
            {paramName, "Ljava/util/Map;"}
        });

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        return (MalFilter) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Compiles from a pre-parsed AST model.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param ast the pre-parsed AST model
     * @return a MalExpression instance
     * @throws Exception if compilation fails
     */
    public MalExpression compileFromModel(final String metricName,
                                          final MALExpressionModel.Expr ast) throws Exception {
        final String className = makeClassName("MalExpr_");

        closureFieldIndex = 0;
        final List<ClosureInfo> closures = new ArrayList<>();
        collectClosures(ast, closures);

        // Generate closure classes first, named by purpose (tag/forEach/instance/decorate)
        // Defer detach until after main class is compiled so types remain in ClassPool
        final List<CtClass> pendingDetach = new ArrayList<>();
        final List<Object> closureInstances = new ArrayList<>();
        final List<String> closureFieldNames = new ArrayList<>();
        final List<String> closureClassNames = new ArrayList<>();
        final java.util.Map<String, Integer> closureNameCounts = new java.util.HashMap<>();
        for (int i = 0; i < closures.size(); i++) {
            final String purpose = closures.get(i).methodName;
            final int count = closureNameCounts.getOrDefault(purpose, 0);
            closureNameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            closureFieldNames.add("_" + suffix);
            final String closureName = dedupClassName(className + "$" + suffix);
            closureClassNames.add(closureName);
            final Object instance = compileClosureClass(
                closureName, closures.get(i), pendingDetach);
            closureInstances.add(instance);
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression"));

        // Add closure fields with actual types
        for (int i = 0; i < closures.size(); i++) {
            ctClass.addField(javassist.CtField.make(
                "public " + closureClassNames.get(i) + " "
                    + closureFieldNames.get(i) + ";", ctClass));
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        this.closureFieldNames = closureFieldNames;
        this.closureFieldIndex = 0;
        final String runBody = generateRunMethod(ast);
        final ExpressionMetadata metadata = extractMetadata(ast);
        final String metadataBody = generateMetadataMethod(metadata);

        if (log.isDebugEnabled()) {
            log.debug("MAL compile [{}] AST: {}", metricName, ast);
            log.debug("MAL compile [{}] run():\n{}", metricName, runBody);
            log.debug("MAL compile [{}] metadata():\n{}", metricName, metadataBody);
        }

        final javassist.CtMethod runMethod = CtNewMethod.make(runBody, ctClass);
        ctClass.addMethod(runMethod);
        addRunLocalVariableTable(runMethod, className, runTempCounter);
        final javassist.CtMethod metaMethod =
            CtNewMethod.make(metadataBody, ctClass);
        ctClass.addMethod(metaMethod);
        addLocalVariableTable(metaMethod, className, new String[][]{
            {"_samples", "Ljava/util/List;"},
            {"_scopeLabels", "Ljava/util/Set;"},
            {"_aggLabels", "Ljava/util/Set;"},
            {"_pct", "[I"}
        });

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        for (final CtClass ct : pendingDetach) {
            ct.detach();
        }
        final MalExpression instance = (MalExpression) clazz.getDeclaredConstructor()
            .newInstance();

        // Set closure fields via reflection
        for (int i = 0; i < closureInstances.size(); i++) {
            final java.lang.reflect.Field field =
                clazz.getField(closureFieldNames.get(i));
            field.set(instance, closureInstances.get(i));
        }

        return instance;
    }

    private static final class ClosureInfo {
        final MALExpressionModel.ClosureArgument closure;
        final String interfaceType;
        final String methodName;
        int fieldIndex;

        ClosureInfo(final MALExpressionModel.ClosureArgument closure,
                    final String interfaceType,
                    final String methodName) {
            this.closure = closure;
            this.interfaceType = interfaceType;
            this.methodName = methodName;
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
            final MALExpressionModel.FunctionCallExpr fce =
                (MALExpressionModel.FunctionCallExpr) expr;
            collectClosuresFromArgs(fce.getFunctionName(),
                fce.getArguments(), closures);
            collectClosuresFromChain(
                fce.getMethodChain(), closures);
        }
    }

    private void collectClosuresFromChain(final List<MALExpressionModel.MethodCall> chain,
                                          final List<ClosureInfo> closures) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            collectClosuresFromArgs(mc.getName(), mc.getArguments(), closures);
        }
    }

    private void collectClosuresFromArgs(final String methodName,
                                         final List<MALExpressionModel.Argument> args,
                                         final List<ClosureInfo> closures) {
        for (final MALExpressionModel.Argument arg : args) {
            if (arg instanceof MALExpressionModel.ClosureArgument) {
                final String interfaceType;
                if ("forEach".equals(methodName)) {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$ForEachFunction";
                } else if ("instance".equals(methodName)) {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$PropertiesExtractor";
                } else if ("decorate".equals(methodName)) {
                    interfaceType = DECORATE_FUNCTION_TYPE;
                } else {
                    interfaceType = "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                        + ".SampleFamilyFunctions$TagFunction";
                }
                final ClosureInfo info = new ClosureInfo(
                    (MALExpressionModel.ClosureArgument) arg,
                    interfaceType, methodName);
                info.fieldIndex = closures.size();
                closures.add(info);
            } else if (arg instanceof MALExpressionModel.ExprArgument) {
                collectClosures(
                    ((MALExpressionModel.ExprArgument) arg).getExpr(), closures);
            }
        }
    }

    private static final String FOR_EACH_FUNCTION_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyFunctions$ForEachFunction";

    private static final String PROPERTIES_EXTRACTOR_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyFunctions$PropertiesExtractor";

    private static final String DECORATE_FUNCTION_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyFunctions$DecorateFunction";

    private static final String METER_ENTITY_FQCN =
        "org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity";

    private static final String RUNTIME_HELPER_FQCN =
        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper";

    private Object compileClosureClass(final String className,
                                       final ClosureInfo info,
                                       final List<CtClass> pendingDetach) throws Exception {
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(info.interfaceType));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final MALExpressionModel.ClosureArgument closure = info.closure;
        final List<String> params = closure.getParams();
        final boolean isForEach = FOR_EACH_FUNCTION_TYPE.equals(info.interfaceType);
        final boolean isPropertiesExtractor =
            PROPERTIES_EXTRACTOR_TYPE.equals(info.interfaceType);

        if (isForEach) {
            // ForEachFunction: void accept(String element, Map<String, String> tags)
            // Closure params: { prefix, tags -> ... } or { element, tags -> ... }
            final String elementParam = params.size() >= 1 ? params.get(0) : "element";
            final String tagsParam = params.size() >= 2 ? params.get(1) : "tags";

            final StringBuilder sb = new StringBuilder();
            sb.append("public void accept(String ").append(elementParam)
              .append(", java.util.Map ").append(tagsParam).append(") {\n");
            for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                generateClosureStatement(sb, stmt, tagsParam);
            }
            sb.append("}\n");

            if (log.isDebugEnabled()) {
                log.debug("ForEach closure body:\n{}", sb);
            }
            final javassist.CtMethod m = CtNewMethod.make(sb.toString(), ctClass);
            ctClass.addMethod(m);
            addLocalVariableTable(m, className, new String[][]{
                {elementParam, "Ljava/lang/String;"},
                {tagsParam, "Ljava/util/Map;"}
            });
        } else if (isPropertiesExtractor) {
            // PropertiesExtractor: Map<String,String> apply(Map<String,String> tags)
            // Body is typically a single map literal expression
            final String paramName = params.isEmpty() ? "it" : params.get(0);

            final StringBuilder sb = new StringBuilder();
            sb.append("public java.util.Map apply(java.util.Map ").append(paramName)
              .append(") {\n");

            // If the body is a single expression statement with a map literal,
            // generate HashMap construction as the return value
            final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
            if (body.size() == 1
                    && body.get(0) instanceof MALExpressionModel.ClosureExprStatement
                    && ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr()
                        instanceof MALExpressionModel.ClosureMapLiteral) {
                final MALExpressionModel.ClosureMapLiteral mapLit =
                    (MALExpressionModel.ClosureMapLiteral)
                        ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
                sb.append("  java.util.Map _result = new java.util.HashMap();\n");
                for (final MALExpressionModel.MapEntry entry : mapLit.getEntries()) {
                    sb.append("  _result.put(\"")
                      .append(escapeJava(entry.getKey())).append("\", ");
                    generateClosureExpr(sb, entry.getValue(), paramName);
                    sb.append(");\n");
                }
                sb.append("  return _result;\n");
            } else {
                for (final MALExpressionModel.ClosureStatement stmt : body) {
                    generateClosureStatement(sb, stmt, paramName);
                }
                sb.append("  return ").append(paramName).append(";\n");
            }
            sb.append("}\n");

            final javassist.CtMethod applyMap =
                CtNewMethod.make(sb.toString(), ctClass);
            ctClass.addMethod(applyMap);
            addLocalVariableTable(applyMap, className, new String[][]{
                {paramName, "Ljava/util/Map;"}
            });
            final javassist.CtMethod applyObj = CtNewMethod.make(
                "public Object apply(Object o) { return apply((java.util.Map) o); }",
                ctClass);
            ctClass.addMethod(applyObj);
            addLocalVariableTable(applyObj, className, new String[][]{
                {"o", "Ljava/lang/Object;"}
            });
        } else if (DECORATE_FUNCTION_TYPE.equals(info.interfaceType)) {
            // DecorateFunction: void accept(MeterEntity)
            // Closure param operates on MeterEntity bean properties (getters/setters).
            final String paramName = params.isEmpty() ? "it" : params.get(0);

            final StringBuilder sb = new StringBuilder();
            sb.append("public void accept(Object _arg) {\n");
            sb.append("  ").append(METER_ENTITY_FQCN).append(" ")
              .append(paramName).append(" = (").append(METER_ENTITY_FQCN)
              .append(") _arg;\n");
            for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                generateClosureStatement(sb, stmt, paramName, true);
            }
            sb.append("}\n");

            if (log.isDebugEnabled()) {
                log.debug("Decorate closure body:\n{}", sb);
            }
            final javassist.CtMethod acceptMethod =
                CtNewMethod.make(sb.toString(), ctClass);
            ctClass.addMethod(acceptMethod);
            addLocalVariableTable(acceptMethod, className, new String[][]{
                {"_arg", "Ljava/lang/Object;"},
                {paramName, "L" + METER_ENTITY_FQCN.replace('.', '/') + ";"}
            });
        } else {
            // TagFunction: Map<String,String> apply(Map<String,String> tags)
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
            final javassist.CtMethod tagApply =
                CtNewMethod.make(sb.toString(), ctClass);
            ctClass.addMethod(tagApply);
            addLocalVariableTable(tagApply, className, new String[][]{
                {paramName, "Ljava/util/Map;"}
            });
            final javassist.CtMethod tagBridge = CtNewMethod.make(
                "public Object apply(Object o) { return apply((java.util.Map) o); }",
                ctClass);
            ctClass.addMethod(tagBridge);
            addLocalVariableTable(tagBridge, className, new String[][]{
                {"o", "Ljava/lang/Object;"}
            });
        }

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        pendingDetach.add(ctClass);
        return clazz.getDeclaredConstructor().newInstance();
    }

    private static final String RUN_VAR = "sf";

    private int runTempCounter;

    private String generateRunMethod(final MALExpressionModel.Expr ast) {
        runTempCounter = 0;
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
     * Emits the expression as a series of {@code sf = ...;} reassignment statements,
     * one per chain call. All results are stored in the single {@code sf} variable.
     * For binary SF op SF expressions, a temporary variable saves the left operand.
     */
    private void generateExprStatements(final StringBuilder sb,
                                        final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            generateMetricExprStatements(
                sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            final double val = ((MALExpressionModel.NumberExpr) expr).getValue();
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(SF).append(".EMPTY.plus(Double.valueOf(")
              .append(val).append("));\n");
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

    private void generateMetricExprStatements(
            final StringBuilder sb, final MALExpressionModel.MetricExpr expr) {
        sb.append("  ").append(RUN_VAR).append(" = ((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY));\n");
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateParenChainStatements(
            final StringBuilder sb, final MALExpressionModel.ParenChainExpr expr) {
        generateExprStatements(sb, expr.getInner());
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateFunctionCallStatements(
            final StringBuilder sb, final MALExpressionModel.FunctionCallExpr expr) {
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
                generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        } else {
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        }
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateBinaryExprStatements(
            final StringBuilder sb, final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();

        final boolean leftIsNumber = left instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(left);
        final boolean rightIsNumber = right instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(right);

        if (leftIsNumber && !rightIsNumber) {
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ");
            switch (op) {
                case ADD:
                    sb.append(RUN_VAR).append(".plus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case SUB:
                    sb.append(RUN_VAR).append(".minus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append(")).negative()");
                    break;
                case MUL:
                    sb.append(RUN_VAR).append(".multiply(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case DIV:
                    sb.append("org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt")
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
              .append(RUN_VAR).append(".").append(opMethodName(op))
              .append("(Double.valueOf(");
            generateScalarExpr(sb, right);
            sb.append("));\n");
        } else {
            // SF op SF: compute left to sf, save to temp, compute right to sf, combine
            generateExprStatements(sb, left);
            final String temp = nextTemp();
            sb.append("  ").append(SF).append(" ").append(temp)
              .append(" = ").append(RUN_VAR).append(";\n");
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(temp).append(".").append(opMethodName(op))
              .append("(").append(RUN_VAR).append(");\n");
        }
    }

    /**
     * Emits each method chain call as a reassignment of {@code sf}.
     */
    private void emitChainStatements(final StringBuilder sb,
                                     final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append('.').append(mc.getName()).append('(');
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
                final boolean primitiveDouble =
                    PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
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

        final boolean leftIsNumber = left instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(left);
        final boolean rightIsNumber = right instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(right);

        if (leftIsNumber && !rightIsNumber) {
            // N op SF -> swap to SF.op(N) with special handling for SUB and DIV
            switch (op) {
                case ADD:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").plus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case SUB:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").minus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append(")).negative()");
                    break;
                case MUL:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").multiply(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case DIV:
                    sb.append("org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt")
                      .append(".MalRuntimeHelper.divReverse(");
                    generateScalarExpr(sb, left);
                    sb.append(", ");
                    generateExpr(sb, right);
                    sb.append(")");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
        } else if (!leftIsNumber && rightIsNumber) {
            // SF op N
            sb.append("(");
            generateExpr(sb, left);
            sb.append(").").append(opMethodName(op))
              .append("(Double.valueOf(");
            generateScalarExpr(sb, right);
            sb.append("))");
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

    /**
     * Methods on SampleFamily whose first argument is a primitive {@code double}.
     * Javassist cannot auto-unbox {@code Double} to {@code double}, so numeric
     * arguments to these methods must be emitted as raw double literals.
     */
    private static final Set<String> PRIMITIVE_DOUBLE_METHODS = Set.of(
        "valueEqual", "valueNotEqual", "valueGreater",
        "valueGreaterEqual", "valueLess", "valueLessEqual"
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
                final boolean primitiveDouble =
                    PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
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

    /**
     * Generates a method call argument, handling numeric ExprArgument
     * specially when the target method expects a primitive double.
     */
    private void generateMethodCallArg(final StringBuilder sb,
                                        final MALExpressionModel.Argument arg,
                                        final boolean primitiveDouble) {
        if (primitiveDouble
                && arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                // Emit raw double literal for methods taking primitive double
                final double num =
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                sb.append(num);
                return;
            }
        }
        generateArgument(sb, arg);
    }

    private static boolean allStringArgs(final List<MALExpressionModel.Argument> args) {
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.StringArgument)
                    && !(arg instanceof MALExpressionModel.NullArgument)) {
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
        } else if (arg instanceof MALExpressionModel.NullArgument) {
            sb.append("null");
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
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                // Numeric literal argument (e.g., valueEqual(1), multiply(100))
                // Emit as Double.valueOf() to match Number parameter types.
                final double num = ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                sb.append("Double.valueOf(").append(num).append(")");
            } else if (innerExpr instanceof MALExpressionModel.MetricExpr
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
        sb.append("this.").append(closureFieldNames.get(closureFieldIndex++));
    }

    private void generateClosureStatement(final StringBuilder sb,
                                          final MALExpressionModel.ClosureStatement stmt,
                                          final String paramName) {
        generateClosureStatement(sb, stmt, paramName, false);
    }

    private void generateClosureStatement(final StringBuilder sb,
                                          final MALExpressionModel.ClosureStatement stmt,
                                          final String paramName,
                                          final boolean beanMode) {
        if (stmt instanceof MALExpressionModel.ClosureAssignment) {
            final MALExpressionModel.ClosureAssignment assign =
                (MALExpressionModel.ClosureAssignment) stmt;
            if (beanMode) {
                // Bean setter: me.attr0 = 'value' → me.setAttr0("value")
                final String keyText = extractConstantKey(assign.getKeyExpr());
                if (keyText != null) {
                    sb.append("    ").append(assign.getMapVar()).append(".set")
                      .append(Character.toUpperCase(keyText.charAt(0)))
                      .append(keyText.substring(1)).append("(");
                    generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                    sb.append(");\n");
                } else {
                    // Fallback to map put for dynamic keys
                    sb.append("    ").append(assign.getMapVar()).append(".put(");
                    generateClosureExpr(sb, assign.getKeyExpr(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                    sb.append(");\n");
                }
            } else {
                sb.append("    ").append(assign.getMapVar()).append(".put(");
                generateClosureExpr(sb, assign.getKeyExpr(), paramName, beanMode);
                sb.append(", ");
                generateClosureExpr(sb, assign.getValue(), paramName, beanMode);
                sb.append(");\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureIfStatement) {
            final MALExpressionModel.ClosureIfStatement ifStmt =
                (MALExpressionModel.ClosureIfStatement) stmt;
            sb.append("    if (");
            generateClosureCondition(sb, ifStmt.getCondition(), paramName, beanMode);
            sb.append(") {\n");
            for (final MALExpressionModel.ClosureStatement s : ifStmt.getThenBranch()) {
                generateClosureStatement(sb, s, paramName, beanMode);
            }
            sb.append("    }\n");
            if (!ifStmt.getElseBranch().isEmpty()) {
                sb.append("    else {\n");
                for (final MALExpressionModel.ClosureStatement s : ifStmt.getElseBranch()) {
                    generateClosureStatement(sb, s, paramName, beanMode);
                }
                sb.append("    }\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureReturnStatement) {
            final MALExpressionModel.ClosureReturnStatement retStmt =
                (MALExpressionModel.ClosureReturnStatement) stmt;
            if (retStmt.getValue() == null) {
                sb.append("    return;\n");
            } else {
                if (beanMode) {
                    sb.append("    return ");
                } else {
                    sb.append("    return (java.util.Map) ");
                }
                generateClosureExpr(sb, retStmt.getValue(), paramName, beanMode);
                sb.append(";\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureVarDecl) {
            final MALExpressionModel.ClosureVarDecl vd =
                (MALExpressionModel.ClosureVarDecl) stmt;
            sb.append("    ").append(vd.getTypeName()).append(" ")
              .append(vd.getVarName()).append(" = ");
            generateClosureExpr(sb, vd.getInitializer(), paramName, beanMode);
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureVarAssign) {
            final MALExpressionModel.ClosureVarAssign va =
                (MALExpressionModel.ClosureVarAssign) stmt;
            sb.append("    ").append(va.getVarName()).append(" = ");
            generateClosureExpr(sb, va.getValue(), paramName, beanMode);
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureExprStatement) {
            sb.append("    ");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprStatement) stmt).getExpr(), paramName,
                beanMode);
            sb.append(";\n");
        }
    }

    private void generateClosureExpr(final StringBuilder sb,
                                     final MALExpressionModel.ClosureExpr expr,
                                     final String paramName) {
        generateClosureExpr(sb, expr, paramName, false);
    }

    private void generateClosureExpr(final StringBuilder sb,
                                     final MALExpressionModel.ClosureExpr expr,
                                     final String paramName,
                                     final boolean beanMode) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            sb.append('"')
              .append(escapeJava(((MALExpressionModel.ClosureStringLiteral) expr).getValue()))
              .append('"');
        } else if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            final double val =
                ((MALExpressionModel.ClosureNumberLiteral) expr).getValue();
            if (val == (int) val) {
                sb.append((int) val);
            } else {
                sb.append(val);
            }
        } else if (expr instanceof MALExpressionModel.ClosureBoolLiteral) {
            sb.append(((MALExpressionModel.ClosureBoolLiteral) expr).isValue());
        } else if (expr instanceof MALExpressionModel.ClosureNullLiteral) {
            sb.append("null");
        } else if (expr instanceof MALExpressionModel.ClosureMapLiteral) {
            final MALExpressionModel.ClosureMapLiteral mapLit =
                (MALExpressionModel.ClosureMapLiteral) expr;
            sb.append("java.util.Map.of(");
            for (int i = 0; i < mapLit.getEntries().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                final MALExpressionModel.MapEntry entry = mapLit.getEntries().get(i);
                sb.append('"').append(escapeJava(entry.getKey())).append("\", ");
                generateClosureExpr(sb, entry.getValue(), paramName, beanMode);
            }
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            generateClosureMethodChain(sb,
                (MALExpressionModel.ClosureMethodChain) expr, paramName, beanMode);
        } else if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            final MALExpressionModel.ClosureBinaryExpr bin =
                (MALExpressionModel.ClosureBinaryExpr) expr;
            sb.append("(");
            generateClosureExpr(sb, bin.getLeft(), paramName, beanMode);
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
            generateClosureExpr(sb, bin.getRight(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureCompTernaryExpr) {
            final MALExpressionModel.ClosureCompTernaryExpr ct =
                (MALExpressionModel.ClosureCompTernaryExpr) expr;
            sb.append("(");
            generateClosureExpr(sb, ct.getLeft(), paramName, beanMode);
            sb.append(comparisonOperator(ct.getOp()));
            generateClosureExpr(sb, ct.getRight(), paramName, beanMode);
            sb.append(" ? ");
            generateClosureExpr(sb, ct.getTrueExpr(), paramName, beanMode);
            sb.append(" : ");
            generateClosureExpr(sb, ct.getFalseExpr(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureTernaryExpr) {
            final MALExpressionModel.ClosureTernaryExpr ternary =
                (MALExpressionModel.ClosureTernaryExpr) expr;
            sb.append("(((Object)(");
            generateClosureExpr(sb, ternary.getCondition(), paramName, beanMode);
            sb.append(")) != null ? (");
            generateClosureExpr(sb, ternary.getTrueExpr(), paramName, beanMode);
            sb.append(") : (");
            generateClosureExpr(sb, ternary.getFalseExpr(), paramName, beanMode);
            sb.append("))");
        } else if (expr instanceof MALExpressionModel.ClosureElvisExpr) {
            final MALExpressionModel.ClosureElvisExpr elvis =
                (MALExpressionModel.ClosureElvisExpr) expr;
            sb.append("java.util.Optional.ofNullable(");
            generateClosureExpr(sb, elvis.getPrimary(), paramName, beanMode);
            sb.append(").orElse(");
            generateClosureExpr(sb, elvis.getFallback(), paramName, beanMode);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureRegexMatchExpr) {
            final MALExpressionModel.ClosureRegexMatchExpr rm =
                (MALExpressionModel.ClosureRegexMatchExpr) expr;
            sb.append(RUNTIME_HELPER_FQCN).append(".regexMatch(String.valueOf(");
            generateClosureExpr(sb, rm.getTarget(), paramName, beanMode);
            sb.append("), \"").append(escapeJava(rm.getPattern())).append("\")");
        } else if (expr instanceof MALExpressionModel.ClosureExprChain) {
            final MALExpressionModel.ClosureExprChain chain =
                (MALExpressionModel.ClosureExprChain) expr;
            final StringBuilder local = new StringBuilder();
            // Cast to String when the chain has method calls (e.g., .split(), .toString())
            // so Javassist can resolve the method on the concrete type.
            final boolean needsCast = chain.getSegments().stream()
                .anyMatch(s -> s instanceof MALExpressionModel.ClosureMethodCallSeg);
            if (needsCast) {
                local.append("((String) ");
            } else {
                local.append("(");
            }
            generateClosureExpr(local, chain.getBase(), paramName, beanMode);
            local.append(")");
            for (final MALExpressionModel.ClosureChainSegment seg : chain.getSegments()) {
                if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    if ("size".equals(mc.getName()) && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i),
                                paramName, beanMode);
                        }
                        local.append(')');
                    }
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append("[(int) ");
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                        paramName, beanMode);
                    local.append(']');
                }
            }
            sb.append(local);
        }
    }

    private void generateClosureMethodChain(
            final StringBuilder sb,
            final MALExpressionModel.ClosureMethodChain chain,
            final String paramName,
            final boolean beanMode) {
        final String target = chain.getTarget();
        final String resolvedTarget = CLOSURE_CLASS_FQCN.getOrDefault(target, target);
        final boolean isClassRef = CLOSURE_CLASS_FQCN.containsKey(target);
        final List<MALExpressionModel.ClosureChainSegment> segs = chain.getSegments();

        // Static class method call: ProcessRegistry.generateVirtualLocalProcess(...)
        if (isClassRef) {
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    local.append('.').append(mc.getName()).append('(');
                    for (int i = 0; i < mc.getArguments().size(); i++) {
                        if (i > 0) {
                            local.append(", ");
                        }
                        generateClosureExpr(local, mc.getArguments().get(i), paramName,
                            beanMode);
                    }
                    local.append(')');
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                }
            }
            sb.append(local);
            return;
        }

        if (segs.isEmpty()) {
            sb.append(resolvedTarget);
            return;
        }

        if (beanMode) {
            // Bean mode: me.serviceName → me.getServiceName()
            // me.layer.name() → me.getLayer().name()
            // parts[0] → parts[0] (array index works as-is)
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    final String name =
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName();
                    if (target.equals(paramName) || local.toString().contains(".get")) {
                        // Bean property on the closure parameter → getter
                        local.append(".get")
                          .append(Character.toUpperCase(name.charAt(0)))
                          .append(name.substring(1)).append("()");
                    } else {
                        // Field access on a local variable (e.g., parts.length)
                        local.append('.').append(name);
                    }
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append('[');
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(), paramName,
                        beanMode);
                    local.append(']');
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    // Groovy .size() on arrays → Java .length (for local vars)
                    if (!target.equals(paramName)
                            && "size".equals(mc.getName())
                            && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i),
                                paramName, beanMode);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
            return;
        }

        // Local variable access (not closure param, not a class reference):
        // e.g., matcher[0][1] → matcher[(int)0][(int)1]  (plain Java array access)
        // e.g., parts.length → parts.length  (field access)
        // e.g., parts.size() → parts.length  (Groovy .size() on arrays)
        if (!target.equals(paramName) && !isClassRef) {
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    local.append("[(int) ");
                    generateClosureExpr(local,
                        ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(), paramName,
                        beanMode);
                    local.append(']');
                } else if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    local.append('.').append(
                        ((MALExpressionModel.ClosureFieldAccess) seg).getName());
                } else if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    final MALExpressionModel.ClosureMethodCallSeg mc =
                        (MALExpressionModel.ClosureMethodCallSeg) seg;
                    // Groovy .size() on arrays → Java .length
                    if ("size".equals(mc.getName()) && mc.getArguments().isEmpty()) {
                        local.append(".length");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
                        }
                        local.append(')');
                    }
                }
            }
            sb.append(local);
            return;
        }

        // Map mode (original): tags.key → tags.get("key")
        if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureFieldAccess) {
            final String key =
                ((MALExpressionModel.ClosureFieldAccess) segs.get(0)).getName();
            sb.append("(String) ").append(resolvedTarget).append(".get(\"")
              .append(escapeJava(key)).append("\")");
        } else if (segs.size() == 1
                && segs.get(0) instanceof MALExpressionModel.ClosureIndexAccess) {
            sb.append("(String) ").append(resolvedTarget).append(".get(");
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureIndexAccess) segs.get(0)).getIndex(), paramName,
                beanMode);
            sb.append(")");
        } else {
            // General chain: build in a local buffer to support safe navigation.
            // The first FieldAccess/IndexAccess is a map .get() returning String.
            // After that, method calls may return other types (e.g., split() →
            // String[]), so subsequent IndexAccess uses array syntax [(int) index].
            final StringBuilder local = new StringBuilder();
            local.append(resolvedTarget);
            boolean pastMapAccess = false;
            for (final MALExpressionModel.ClosureChainSegment seg : segs) {
                if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                    final String name = ((MALExpressionModel.ClosureFieldAccess) seg)
                        .getName();
                    if (!pastMapAccess) {
                        final String prior = local.toString();
                        local.setLength(0);
                        local.append("((String) ").append(prior).append(".get(\"")
                          .append(escapeJava(name)).append("\"))");
                        pastMapAccess = true;
                    } else {
                        local.append('.').append(name);
                    }
                } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                    if (!pastMapAccess) {
                        final String prior2 = local.toString();
                        local.setLength(0);
                        local.append("((String) ").append(prior2).append(".get(");
                        generateClosureExpr(local,
                            ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                            paramName, beanMode);
                        local.append("))");
                        pastMapAccess = true;
                    } else {
                        local.append("[(int) ");
                        generateClosureExpr(local,
                            ((MALExpressionModel.ClosureIndexAccess) seg).getIndex(),
                            paramName, beanMode);
                        local.append(']');
                    }
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
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
                        }
                        local.append("))");
                    } else {
                        local.append('.').append(mc.getName()).append('(');
                        for (int i = 0; i < mc.getArguments().size(); i++) {
                            if (i > 0) {
                                local.append(", ");
                            }
                            generateClosureExpr(local, mc.getArguments().get(i), paramName,
                                beanMode);
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
        generateClosureCondition(sb, cond, paramName, false);
    }

    private void generateClosureCondition(final StringBuilder sb,
                                          final MALExpressionModel.ClosureCondition cond,
                                          final String paramName,
                                          final boolean beanMode) {
        if (cond instanceof MALExpressionModel.ClosureComparison) {
            final MALExpressionModel.ClosureComparison cc =
                (MALExpressionModel.ClosureComparison) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(", ");
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    sb.append(")");
                    break;
                default:
                    generateClosureExpr(sb, cc.getLeft(), paramName, beanMode);
                    sb.append(comparisonOperator(cc.getOp()));
                    generateClosureExpr(sb, cc.getRight(), paramName, beanMode);
                    break;
            }
        } else if (cond instanceof MALExpressionModel.ClosureLogical) {
            final MALExpressionModel.ClosureLogical lc =
                (MALExpressionModel.ClosureLogical) cond;
            sb.append("(");
            generateClosureCondition(sb, lc.getLeft(), paramName, beanMode);
            sb.append(lc.getOp() == MALExpressionModel.LogicalOp.AND ? " && " : " || ");
            generateClosureCondition(sb, lc.getRight(), paramName, beanMode);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureNot) {
            sb.append("!(");
            generateClosureCondition(sb,
                ((MALExpressionModel.ClosureNot) cond).getInner(), paramName, beanMode);
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureExprCondition) {
            final MALExpressionModel.ClosureExpr condExpr =
                ((MALExpressionModel.ClosureExprCondition) cond).getExpr();
            if (isBooleanExpression(condExpr)) {
                generateClosureExpr(sb, condExpr, paramName, beanMode);
            } else {
                sb.append("(");
                generateClosureExpr(sb, condExpr, paramName, beanMode);
                sb.append(" != null)");
            }
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
            generateClosureExpr(sb, ic.getExpr(), paramName, beanMode);
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
        final String mdClass = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata";
        final String scopeTypeClass = "org.apache.skywalking.oap.server.core.analysis.meter.ScopeType";
        final String dsTypeClass = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType";

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

    /**
     * Whether the expression is a scalar (number-producing) function like {@code time()}.
     */
    private static boolean isScalarFunction(final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            final String fn = ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            return "time".equals(fn);
        }
        return false;
    }

    /**
     * Generate code for a scalar expression (literal number or scalar function).
     */
    private void generateScalarExpr(final StringBuilder sb,
                                    final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            sb.append(((MALExpressionModel.NumberExpr) expr).getValue());
        } else if (isScalarFunction(expr)) {
            final String fn = ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            if ("time".equals(fn)) {
                sb.append("(double) java.time.Instant.now().getEpochSecond()");
            }
        }
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

    /**
     * Extracts a constant string key from a closure expression (used for bean setter naming).
     * Returns the key string if the expression is a string literal, or null otherwise.
     */
    private static String extractConstantKey(final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            return ((MALExpressionModel.ClosureStringLiteral) expr).getValue();
        }
        return null;
    }

    /**
     * Checks whether a closure expression returns {@code boolean} by inspecting
     * the last method call in the chain against {@link String} method signatures.
     * MAL closure params are always {@code Map<String, String>}, so chained
     * methods operate on {@code String}.
     */
    private static boolean isBooleanExpression(final MALExpressionModel.ClosureExpr expr) {
        String lastMethodName = null;
        if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            final List<MALExpressionModel.ClosureChainSegment> segs =
                ((MALExpressionModel.ClosureMethodChain) expr).getSegments();
            for (int i = segs.size() - 1; i >= 0; i--) {
                if (segs.get(i) instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    lastMethodName =
                        ((MALExpressionModel.ClosureMethodCallSeg) segs.get(i)).getName();
                    break;
                }
            }
        }
        if (lastMethodName == null) {
            return false;
        }
        for (final java.lang.reflect.Method m : String.class.getMethods()) {
            if (m.getName().equals(lastMethodName)
                    && m.getReturnType() == boolean.class) {
                return true;
            }
        }
        return false;
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
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final List<ClosureInfo> closures = new ArrayList<>();
        collectClosures(ast, closures);
        // Build field names for source generation
        final List<String> fieldNames = new ArrayList<>();
        final java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
        for (final ClosureInfo ci : closures) {
            final String purpose = ci.methodName;
            final int count = nameCounts.getOrDefault(purpose, 0);
            nameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            fieldNames.add("_" + suffix);
        }
        this.closureFieldNames = fieldNames;
        this.closureFieldIndex = 0;
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
