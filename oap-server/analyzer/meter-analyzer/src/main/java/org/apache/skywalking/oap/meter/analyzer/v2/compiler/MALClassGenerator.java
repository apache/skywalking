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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Public API for compiling MAL expressions into {@link MalExpression} implementation classes.
 *
 * <p>Orchestrates the compilation pipeline:
 * <ol>
 *   <li>Parse expression string → AST ({@link MALScriptParser})</li>
 *   <li>Collect closures → companion classes ({@link MALClosureCodegen})</li>
 *   <li>Generate {@code run()} method body ({@link MALExprCodegen})</li>
 *   <li>Extract metadata ({@link MALMetadataExtractor})</li>
 *   <li>Create Javassist class, add methods, load ({@link MALBytecodeHelper})</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   MALClassGenerator gen = new MALClassGenerator();
 *   MalExpression expr = gen.compile("my_metric", "metric.sum(['svc']).rate('PT1M')");
 *   SampleFamily result = expr.run(samples);
 * </pre>
 */
@Slf4j
public final class MALClassGenerator {

    private final ClassPool classPool;
    private final MALBytecodeHelper bytecodeHelper;

    public MALClassGenerator() {
        this(createClassPool());
        if (StringUtil.isNotEmpty(System.getenv("SW_DYNAMIC_CLASS_ENGINE_DEBUG"))) {
            bytecodeHelper.setClassOutputDir(
                new File(WorkPath.getPath().getParentFile(), "mal-rt"));
        }
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
        this.bytecodeHelper = new MALBytecodeHelper();
    }

    public void setClassOutputDir(final File dir) {
        bytecodeHelper.setClassOutputDir(dir);
    }

    public void setClassNameHint(final String hint) {
        bytecodeHelper.setClassNameHint(hint);
    }

    public void setYamlSource(final String yamlSource) {
        bytecodeHelper.setYamlSource(yamlSource);
    }

    // Package-private accessors for MALClosureCodegen back-reference
    MALBytecodeHelper getBytecodeHelper() {
        return bytecodeHelper;
    }

    // ==================== Public compilation API ====================

    /**
     * Compiles a MAL expression string into a {@link MalExpression}.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param expression the MAL expression string, e.g.
     *                   {@code "metric.sum(['svc']).service(['svc'], Layer.GENERAL)"}
     * @return a compiled MalExpression instance
     */
    public MalExpression compile(final String metricName,
                                 final String expression) throws Exception {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final String saved = bytecodeHelper.getClassNameHint();
        if (bytecodeHelper.getClassNameHint() == null) {
            bytecodeHelper.setClassNameHint(metricName);
        }
        try {
            return compileFromModel(metricName, ast);
        } finally {
            bytecodeHelper.setClassNameHint(saved);
        }
    }

    /**
     * Compiles a MAL filter closure into a {@link MalFilter}.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     * @return a compiled MalFilter instance
     */
    @SuppressWarnings("unchecked")
    public MalFilter compileFilter(final String filterExpression) throws Exception {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final String className = bytecodeHelper.makeClassName("MalFilter_");

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter"));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final String filterBody = generateFilterTestBody(closure);
        if (log.isDebugEnabled()) {
            log.debug("MAL compileFilter AST: {}", closure);
            log.debug("MAL compileFilter test():\n{}", filterBody);
        }

        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final javassist.CtMethod testMethod =
            CtNewMethod.make(filterBody, ctClass);
        ctClass.addMethod(testMethod);
        bytecodeHelper.addLocalVariableTable(testMethod, className,
            new String[][]{{paramName, "Ljava/util/Map;"}});
        bytecodeHelper.addLineNumberTable(testMethod, 2);
        MALBytecodeHelper.setSourceFile(ctClass,
            bytecodeHelper.formatSourceFileName(
                bytecodeHelper.getClassNameHint() != null
                    ? bytecodeHelper.getClassNameHint() : "filter"));

        bytecodeHelper.writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        return (MalFilter) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Compiles from a pre-parsed AST model.
     */
    public MalExpression compileFromModel(final String metricName,
                                          final MALExpressionModel.Expr ast) throws Exception {
        final String className = bytecodeHelper.makeClassName("MalExpr_");

        // 1. Collect closures → companion classes
        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final List<MALClosureCodegen.ClosureInfo> closures = new ArrayList<>();
        cc.collectClosures(ast, closures);

        final List<String> closureFieldNames = new ArrayList<>();
        final List<String> closureInterfaceTypes = new ArrayList<>();
        final java.util.Map<String, Integer> closureNameCounts =
            new java.util.HashMap<>();
        for (int i = 0; i < closures.size(); i++) {
            final String purpose = closures.get(i).methodName;
            final int count = closureNameCounts.getOrDefault(purpose, 0);
            closureNameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            closureFieldNames.add("_" + suffix);
            closureInterfaceTypes.add(closures.get(i).interfaceType);
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression"));

        // 2. Generate companion classes
        final List<CtClass> companionClasses = new ArrayList<>();
        for (int i = 0; i < closures.size(); i++) {
            final CtClass companion = cc.makeCompanionClass(
                ctClass, closureFieldNames.get(i), closures.get(i));
            companionClasses.add(companion);
        }

        for (int i = 0; i < closures.size(); i++) {
            ctClass.addField(javassist.CtField.make(
                "public static final " + closureInterfaceTypes.get(i) + " "
                    + closureFieldNames.get(i) + ";", ctClass));
        }

        if (!closures.isEmpty()) {
            final StringBuilder staticInit = new StringBuilder();
            for (int i = 0; i < closures.size(); i++) {
                staticInit.append(closureFieldNames.get(i))
                          .append(" = new ")
                          .append(companionClasses.get(i).getName())
                          .append("();\n");
            }
            ctClass.makeClassInitializer()
                   .setBody("{ " + staticInit + "}");
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        // 3. Generate run() method
        final MALExprCodegen exprCodegen = new MALExprCodegen(closureFieldNames);
        final String runBody = exprCodegen.generateRunMethod(ast);

        // 4. Extract metadata
        final ExpressionMetadata metadata =
            MALMetadataExtractor.extractMetadata(ast);
        final String metadataBody =
            MALMetadataExtractor.generateMetadataMethod(metadata);

        if (log.isDebugEnabled()) {
            log.debug("MAL compile [{}] AST: {}", metricName, ast);
            log.debug("MAL compile [{}] run():\n{}", metricName, runBody);
            log.debug("MAL compile [{}] metadata():\n{}",
                      metricName, metadataBody);
        }

        // 5. Add methods and bytecode attributes
        final javassist.CtMethod runMethod =
            CtNewMethod.make(runBody, ctClass);
        ctClass.addMethod(runMethod);
        bytecodeHelper.addRunLocalVariableTable(
            runMethod, className, exprCodegen.getDeclaredVars());
        bytecodeHelper.addLineNumberTable(runMethod, 1);

        final javassist.CtMethod metaMethod =
            CtNewMethod.make(metadataBody, ctClass);
        ctClass.addMethod(metaMethod);
        bytecodeHelper.addLocalVariableTable(metaMethod, className,
            new String[][]{
                {"_samples", "Ljava/util/List;"},
                {"_scopeLabels", "Ljava/util/Set;"},
                {"_aggLabels", "Ljava/util/Set;"},
                {"_pct", "[I"}
            });
        MALBytecodeHelper.setSourceFile(ctClass,
            bytecodeHelper.formatSourceFileName(metricName));

        // 6. Load companions, then main class
        for (final CtClass companion : companionClasses) {
            bytecodeHelper.writeClassFile(companion);
            companion.toClass(MalExpressionPackageHolder.class);
            companion.detach();
        }

        bytecodeHelper.writeClassFile(ctClass);

        final Class<?> clazz =
            ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();

        return (MalExpression) clazz.getDeclaredConstructor().newInstance();
    }

    // ==================== Debug/source generation ====================

    /**
     *
     * Generates the Java source of the {@code run()} method for debugging.
     */
    public String generateSource(final String expression) {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final List<MALClosureCodegen.ClosureInfo> closures = new ArrayList<>();
        cc.collectClosures(ast, closures);

        final List<String> fieldNames = new ArrayList<>();
        final java.util.Map<String, Integer> nameCounts =
            new java.util.HashMap<>();
        for (final MALClosureCodegen.ClosureInfo ci : closures) {
            final String purpose = ci.methodName;
            final int count = nameCounts.getOrDefault(purpose, 0);
            nameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            fieldNames.add("_" + suffix);
        }

        final MALExprCodegen exprCodegen = new MALExprCodegen(fieldNames);
        return exprCodegen.generateRunMethod(ast);
    }

    /**
     * Generates the Java source of the filter {@code test()} method for debugging.
     */
    public String generateFilterSource(final String filterExpression) {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);
        return generateFilterTestBody(closure);
    }

    /**
     * Generates the {@code test(Map)} method body for a filter closure.
     * Shared by {@link #compileFilter} (compilation) and {@link #generateFilterSource} (debug).
     *
     * <p>For {@code { tags -> tags.job_name == 'mysql-monitoring' }}, generates:
     * <pre>
     * public boolean test(java.util.Map tags) {
     *   return java.util.Objects.equals((String) tags.get("job_name"), "mysql-monitoring");
     * }
     * </pre>
     */
    private String generateFilterTestBody(
            final MALExpressionModel.ClosureArgument closure) {
        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
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
                cc.generateClosureCondition(
                    sb, (MALExpressionModel.ClosureCondition) expr, paramName);
                sb.append(";\n");
            } else {
                sb.append("  Object _v = ");
                cc.generateClosureExpr(sb, expr, paramName);
                sb.append(";\n");
                sb.append(
                    "  return _v != null && !Boolean.FALSE.equals(_v);\n");
            }
        } else {
            for (final MALExpressionModel.ClosureStatement stmt : body) {
                cc.generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return false;\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
