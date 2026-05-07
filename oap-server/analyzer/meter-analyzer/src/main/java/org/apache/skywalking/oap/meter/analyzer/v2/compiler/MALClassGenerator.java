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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder;
import org.apache.skywalking.oap.server.core.classloader.BytecodeClassDefiner;
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

    /**
     * When non-null, generated MAL classes (MalExpression, MalFilter, closure companions)
     * are defined in this ClassLoader via {@code ctClass.toClass(loader, null)} — used by
     * the runtime-rule hot-update path so the whole MAL class family for one YAML file
     * lives in a single per-file {@code RuleClassLoader} and drops together on unregister.
     * Null = legacy startup path: uses neighbor-class form with
     * {@link MalExpressionPackageHolder} so classes land in the OAP app loader.
     */
    private final ClassLoader targetClassLoader;

    public MALClassGenerator() {
        this(createClassPool(), null);
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
        this(classPool, null);
    }

    /**
     * Runtime-rule constructor: caller supplies the per-file {@link ClassPool} (already
     * scoped to a per-file {@code RuleClassLoader} via {@code LoaderClassPath}) and the
     * target {@link ClassLoader}. Every class this generator emits will be loaded into
     * {@code targetClassLoader} rather than the OAP app loader.
     */
    public MALClassGenerator(final ClassPool classPool, final ClassLoader targetClassLoader) {
        this.classPool = classPool;
        this.bytecodeHelper = new MALBytecodeHelper();
        this.targetClassLoader = targetClassLoader;
        // Per-file loader mode: generated class names are scoped to this loader's namespace so
        // the helper can skip its process-wide dedup set (the leak finding).
        this.bytecodeHelper.setPerFileClassLoader(targetClassLoader != null);
    }

    /**
     * Optional content hash threaded into every generated rule's {@code GateHolder}
     * constructor argument. Stamped onto every captured debug record so a
     * UI / CLI session can detect mid-session hot-update boundaries. Defaults
     * to the empty string when callers don't supply one (boot-time loaders that
     * don't track per-rule hashes).
     */
    private String content = "";

    public void setClassOutputDir(final File dir) {
        bytecodeHelper.setClassOutputDir(dir);
    }

    public void setClassNameHint(final String hint) {
        bytecodeHelper.setClassNameHint(hint);
    }

    public void setYamlSource(final String yamlSource) {
        bytecodeHelper.setYamlSource(yamlSource);
    }

    /**
     * Sets the content hash baked into the next compiled rule's
     * {@code GateHolder} constructor argument. Caller-supplied so the
     * runtime-rule hot-update path can pass its already-computed
     * {@code ContentHash} value through unchanged. Pass {@code null} or
     * the empty string to skip the stamp — the generated holder still works,
     * but captured records carry an empty hash.
     */
    public void setContent(final String content) {
        this.content = content == null ? "" : content;
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
        bytecodeHelper.writeSourceFile(ctClass, wrapMalFilterSource(ctClass, filterBody));

        final Class<?> clazz = defineClass(ctClass);
        ctClass.detach();
        return (MalFilter) clazz.getDeclaredConstructor().newInstance();
    }

    /** Wraps the filter test() method body as a compilable class envelope. */
    private static String wrapMalFilterSource(final CtClass ctClass, final String filterBody) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(ctClass.getPackageName()).append(";\n\n");
        sb.append("public class ").append(ctClass.getSimpleName())
          .append(" implements org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter {\n\n");
        sb.append("    ").append(filterBody.replace("\n", "\n    ")).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Loads a generated class through the configured {@link #targetClassLoader} when set
     * (runtime-rule hot-update path: class lands in the per-file {@code RuleClassLoader}),
     * or via the neighbor-class form when {@code targetClassLoader} is {@code null}
     * (startup path: class lands in the OAP app loader alongside
     * {@link MalExpressionPackageHolder}).
     *
     * <p>When {@code targetClassLoader} implements
     * {@link org.apache.skywalking.oap.server.core.classloader.BytecodeClassDefiner
     * BytecodeClassDefiner} (the runtime-rule {@code RuleClassLoader} does), we hand
     * the loader the {@code CtClass.toBytecode()} bytes and let it invoke its public
     * {@code defineClass} directly — no Javassist {@code toClass(loader,
     * ProtectionDomain)} reflection, no {@code --add-opens java.base/java.lang}
     * requirement on JDK 17+. Otherwise we fall back to the legacy 2-arg toClass for
     * back-compat, but no shipped loader uses that path today.
     */
    private Class<?> defineClass(final CtClass ctClass) throws javassist.CannotCompileException {
        if (targetClassLoader != null) {
            if (targetClassLoader instanceof BytecodeClassDefiner) {
                try {
                    return ((BytecodeClassDefiner) targetClassLoader)
                        .defineClass(ctClass.getName(), ctClass.toBytecode());
                } catch (final IOException e) {
                    throw new javassist.CannotCompileException(
                        "failed to serialise " + ctClass.getName() + " bytes", e);
                }
            }
            return ctClass.toClass(targetClassLoader, null);
        }
        return ctClass.toClass(MalExpressionPackageHolder.class);
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
        final Map<String, Integer> closureNameCounts =
            new HashMap<>();
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

        // 2.5. Emit the per-rule GateHolder instance field + accessor so the
        // run() body's gate-checks have a target to read. Field is final +
        // initialized inline so the value is stamped at construction; the
        // accessor returns the same instance every call (no per-call alloc).
        emitDebugHolderMembers(ctClass);

        // 3. Generate run() method — pass metricName as ruleName so probe call sites
        // emitted by MALExprCodegen and MALMethodChainCodegen carry it as their first arg.
        final MALExprCodegen exprCodegen = new MALExprCodegen(closureFieldNames, metricName);
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
            defineClass(companion);
            companion.detach();
        }

        bytecodeHelper.writeClassFile(ctClass);
        bytecodeHelper.writeSourceFile(ctClass, wrapMalExpressionSource(
            ctClass, closureFieldNames, closureInterfaceTypes, runBody, metadataBody));

        final Class<?> clazz = defineClass(ctClass);
        ctClass.detach();

        return (MalExpression) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Wraps the run() / metadata() method bodies plus closure field stubs
     * + GateHolder field as a compilable class envelope.
     */
    private static String wrapMalExpressionSource(
            final CtClass ctClass,
            final List<String> closureFieldNames,
            final List<String> closureInterfaceTypes,
            final String runBody,
            final String metadataBody) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(ctClass.getPackageName()).append(";\n\n");
        sb.append("public class ").append(ctClass.getSimpleName())
          .append(" implements org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression {\n\n");
        for (int i = 0; i < closureFieldNames.size(); i++) {
            sb.append("    public static final ").append(closureInterfaceTypes.get(i))
              .append(' ').append(closureFieldNames.get(i)).append(";\n");
        }
        if (org.apache.skywalking.oap.server.core.dsldebug.DSLDebugCodegenSwitch.isInjectionEnabled()) {
            sb.append("    public final org.apache.skywalking.oap.server.core.dsldebug.GateHolder debug;\n");
            sb.append("    public org.apache.skywalking.oap.server.core.dsldebug.GateHolder debugHolder() { return this.debug; }\n");
        }
        sb.append("\n    ").append(runBody.replace("\n", "\n    ")).append("\n\n");
        sb.append("    ").append(metadataBody.replace("\n", "\n    ")).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Adds the {@code public final GateHolder debug} instance field and
     * the {@code debugHolder()} accessor on the generated rule class.
     * Both are mandatory for every compiled rule because the {@code run()}
     * codegen embeds {@code if (this.debug.isGateOn()) MALDebug.captureXxx(this.debug, ...)}
     * call sites that read the field directly. The constructor arg is the
     * configured {@link #content}, escaped as a Java string literal.
     */
    private void emitDebugHolderMembers(final CtClass ctClass) throws javassist.CannotCompileException {
        if (!org.apache.skywalking.oap.server.core.dsldebug.DSLDebugCodegenSwitch.isInjectionEnabled()) {
            // Injection off — generated class inherits MalExpression.debugHolder()'s
            // default null return, no GateHolder field is emitted, and the captureXxx
            // call sites the helpers below would emit are also skipped. The compiled
            // bytecode is byte-identical to a build without SWIP-13.
            return;
        }
        final String holderFqcn = "org.apache.skywalking.oap.server.core.dsldebug.GateHolder";
        final String escapedContent = MALCodegenHelper.escapeJava(content);
        // Per-rule capture binding — instance field, lowercase per Java
        // convention (it's a final but not a static-final constant).
        ctClass.addField(javassist.CtField.make(
            "public final " + holderFqcn + " debug = new " + holderFqcn
                + "(\"" + escapedContent + "\");",
            ctClass));
        ctClass.addMethod(CtNewMethod.make(
            "public " + holderFqcn + " debugHolder() { return this.debug; }",
            ctClass));
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
        final Map<String, Integer> nameCounts =
            new HashMap<>();
        for (final MALClosureCodegen.ClosureInfo ci : closures) {
            final String purpose = ci.methodName;
            final int count = nameCounts.getOrDefault(purpose, 0);
            nameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            fieldNames.add("_" + suffix);
        }

        // Source view (debug helper) — no compiled class, so ruleName is empty and the
        // emitted source still includes probe lines with an empty rule string.
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
