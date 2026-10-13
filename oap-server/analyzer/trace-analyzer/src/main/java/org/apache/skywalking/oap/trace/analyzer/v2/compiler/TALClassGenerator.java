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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.dsl.DslClassNaming;
import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.classloader.BytecodeClassDefiner;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalExpressionPackageHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;

/**
 * Compiles one TAL rule into a class implementing {@link TalExpression}.
 *
 * <p>The pipeline is the one every SkyWalking DSL uses: the parsed model becomes Java source, Javassist compiles
 * it, and the class is loaded with a local-variable table and a source reference so a stack trace names the rule
 * file. Blocks become private methods guarded by the abort flag; every method takes the context explicitly, so
 * nothing rides on a {@code ThreadLocal}.
 */
@Slf4j
public final class TALClassGenerator {
    private static final String PACKAGE = "org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.";
    private static final String FILTER_SPEC = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec";
    private static final String EXEC_CTX = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext";
    private static final String HELPER = "org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper";
    private static final String EXPRESSION = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression";

    private final ClassPool classPool;
    private final ClassLoader targetClassLoader;
    /**
     * Where the generated {@code .class} and the Java source Javassist compiled are written. Null in normal
     * operation: {@code resolveClassDumpDir} returns null unless {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG} is set,
     * and it builds its path from the OAP install directory, which a test JVM does not have. A test that wants
     * the dump sets it explicitly through {@link #setClassOutputDir}.
     */
    private File classOutputDir;

    /**
     * The rule's declared input type, which the {@code span} root resolves against.
     */
    @Setter
    private Class<?> inputType;

    /**
     * The record the rule builds, or null when it declares no {@code outputType}.
     */
    @Setter
    private Class<?> outputType;

    /**
     * Write the generated class and the Java source Javassist compiled to this directory, whatever the
     * environment says. Meant for tests: {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG} resolves its path from the OAP
     * install directory, which a test JVM does not have.
     *
     * @param dir the directory; the writer creates it on first write
     */
    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    @Setter
    private String classNameHint;

    @Setter
    private DslSourceRef sourceRef;

    @Setter
    private String metricPrefix;

    /**
     * The verbatim rule text, stamped on debug captures so the UI can render the source inline.
     */
    @Setter
    private String content;

    /**
     * Every metric the compiled rule declares, available after {@link #compile}.
     */
    @Getter
    private List<TALMetricDeclaration> metricDeclarations = List.of();

    public TALClassGenerator() {
        this(ClassPool.getDefault(), null);
    }

    /**
     * For a caller that supplies its own pool, scoped to its own loader, so the generated class is defined there
     * rather than in the OAP app loader.
     *
     * @param classPool         the pool to make the class in
     * @param targetClassLoader the loader to define it in, or null for the app loader
     */
    public TALClassGenerator(final ClassPool classPool, final ClassLoader targetClassLoader) {
        this.classPool = classPool;
        this.targetClassLoader = targetClassLoader;
        this.classOutputDir = DslGeneratedFileWriter.resolveClassDumpDir("tal");
    }

    /**
     * Parse, generate and load one rule.
     *
     * @param dsl the rule's DSL text
     * @return the compiled rule, ready to run against spans
     * @throws Exception when the rule does not parse, does not validate, or does not compile
     */
    public TalExpression compile(final String dsl) throws Exception {
        final TALScriptModel model = TALScriptParser.parse(dsl);
        final TALGenCtx genCtx = new TALGenCtx(new TALSpanRootResolver(inputType), inputType, outputType);
        genCtx.metricPrefix = metricPrefix;
        genCtx.ruleName = classNameHint == null ? "tal" : classNameHint;

        final String executeBody = generateExecuteMethod(model, genCtx);
        if (outputType != null && genCtx.sinkCount == 0) {
            throw new IllegalArgumentException(
                "the rule declares outputType " + outputType.getSimpleName()
                    + " and needs a sink {} block, which decides whether its record is written");
        }
        this.metricDeclarations = List.copyOf(dedupeDeclarations(genCtx.metricDeclarations));

        final String className = DslClassNaming.allocate(
            PACKAGE + DslClassNaming.stem(sourceRef, genCtx.ruleName, TALConfigs.TAL_CATALOG),
            targetClassLoader == null);

        if (log.isDebugEnabled()) {
            log.debug("TAL compile execute():\n{}", executeBody);
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(EXPRESSION));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final CtMethod execMethod = CtNewMethod.make(executeBody, ctClass);
        ctClass.addMethod(execMethod);

        final List<String[]> execLvt = new ArrayList<>();
        execLvt.add(new String[] {"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"});
        execLvt.add(new String[] {"ctx", "L" + EXEC_CTX.replace('.', '/') + ";"});
        execLvt.add(new String[] {"h", "L" + HELPER.replace('.', '/') + ";"});
        // In the order execute() declares them, see generateExecuteMethod.
        if (outputType != null) {
            execLvt.add(new String[] {"_o", "L" + outputType.getName().replace('.', '/') + ";"});
        }
        if (genCtx.usedProtoAccess) {
            if (inputType != null) {
                execLvt.add(new String[] {"_p", "L" + inputType.getName().replace('.', '/') + ";"});
            }
            execLvt.addAll(genCtx.protoLvtVars);
        }
        execLvt.addAll(genCtx.localVarLvtVars);
        DslGeneratedFileWriter.addLocalVariableTable(execMethod, className, execLvt.toArray(new String[0][]));

        final String sourceText = buildSourceText(ctClass, genCtx, executeBody);
        if (sourceRef != null) {
            DslGeneratedFileWriter.setSourceFile(ctClass, sourceRef.sourceFileOf(ctClass.getSimpleName()));
        }
        DslGeneratedFileWriter.attachSignatureLine(
            execMethod, DslGeneratedFileWriter.lineOfMethod(sourceText, "execute"));

        DslGeneratedFileWriter.writeClassFile(classOutputDir, ctClass);
        DslGeneratedFileWriter.writeSourceFile(classOutputDir, ctClass.getSimpleName(), sourceText);

        final Class<?> clazz = BytecodeClassDefiner.define(
            ctClass, targetClassLoader, TalExpressionPackageHolder.class);
        ctClass.detach();
        return (TalExpression) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Install the storage model for every metric the rule declared. Runs before the rule sees a span; on the
     * boot path {@code create} is create-if-absent, as it is for MAL.
     *
     * @param meterSystem the meter system to register with
     */
    public void createMetrics(final MeterSystem meterSystem) {
        for (final TALMetricDeclaration declaration : metricDeclarations) {
            meterSystem.create(declaration.fullName, declaration.function,
                ScopeType.valueOf(declaration.scopeType));
            if (declaration.labelLimit > 0) {
                meterSystem.limitLabels(declaration.fullName, declaration.labelLimit);
            }
        }
    }

    /**
     * Two blocks declaring the same metric with the same shape share one metric, which is how {@code if}
     * branches feed one series; the same name with a different function or scope is a compile error naming both.
     */
    private static List<TALMetricDeclaration> dedupeDeclarations(final List<TALMetricDeclaration> declarations) {
        final Map<String, TALMetricDeclaration> byName = new LinkedHashMap<>();
        final Set<TALMetricDeclaration> distinct = new LinkedHashSet<>(declarations);
        for (final TALMetricDeclaration declaration : distinct) {
            final TALMetricDeclaration previous = byName.put(declaration.fullName, declaration);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "metric " + declaration.fullName + " is declared twice with different shapes: "
                        + previous + " and " + declaration);
            }
        }
        return new ArrayList<>(byName.values());
    }

    private String generateExecuteMethod(final TALScriptModel model, final TALGenCtx genCtx) {
        genCtx.resetProtoVars();

        final StringBuilder bodyContent = new StringBuilder();
        // Everything is generated into one method, so an abort {} anywhere returns from it and nothing after it
        // runs, and a filter-level def is in scope inside the extractor.
        TALBlockCodegen.generateStatements(bodyContent, model.getStatements(), genCtx, false);

        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(EXEC_CTX).append(" ctx) {\n");
        sb.append("  ").append(HELPER).append(" h = new ").append(HELPER)
          .append("(ctx, filterSpec);\n");

        if (outputType != null) {
            final String outName = outputType.getName();
            sb.append("  ").append(outName).append(" _o = new ").append(outName).append("();\n");
            sb.append("  ctx.setOutput(_o);\n");
            sb.append("  ctx.outputAsBuilder().bindInput(ctx.getMeta(), ctx.getInput());\n");
        }

        if (genCtx.usedProtoAccess) {
            if (inputType != null) {
                final String typeName = inputType.getName();
                sb.append("  ").append(typeName).append(" _p = (")
                  .append(typeName).append(") ctx.getInput();\n");
            }
            sb.append(genCtx.protoVarDecls);
        }
        if (genCtx.localVarDecls.length() > 0) {
            sb.append(genCtx.localVarDecls);
        }

        sb.append(bodyContent);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Assemble the exact Java source handed to Javassist, so the line numbers attached to the methods refer to
     * text that exists rather than to an envelope reconstructed later.
     */
    private String buildSourceText(final CtClass ctClass, final TALGenCtx genCtx, final String executeBody) {
        final StringBuilder out = new StringBuilder();
        out.append("// generated from ")
           .append(sourceRef == null ? "<unknown>" : sourceRef.toString()).append('\n');
        out.append("public class ").append(ctClass.getSimpleName())
           .append(" implements ").append(EXPRESSION).append(" {\n");
        out.append(executeBody);
        out.append("}\n");
        return out.toString();
    }

}
