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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtMethod;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfigs;
import org.apache.skywalking.oap.server.core.dsl.DslClassNaming;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.classloader.BytecodeClassDefiner;
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import javassist.CtField;

/**
 * Generates {@link LalExpression} implementation classes from
 * {@link LALScriptModel} AST using Javassist bytecode generation.
 *
 * <p>Generates a single class with {@code execute()} and private helper
 * methods — no consumer classes or callback indirection.
 *
 * <p>Block-level code generation (extractor, sink, condition, value access)
 * is delegated to {@link LALBlockCodegen}. Static utility constants and
 * methods live in {@link LALCodegenHelper}.
 */
@Slf4j
public final class LALClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.";

    private static final String FILTER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec";
    private static final String EXEC_CTX =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext";
    private static final String H =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper";

    private final ClassPool classPool;
    /**
     * When non-null, generated LAL classes are defined in this ClassLoader via
     * {@code ctClass.toClass(loader, null)} — used by the runtime-rule hot-update path so one
     * YAML file's full LAL class family lives in a single per-file {@code RuleClassLoader} and
     * drops together on unregister. Null = legacy startup path: uses the neighbor-class form
     * with {@link LalExpressionPackageHolder} so classes land in the OAP app loader.
     */
    private final ClassLoader targetClassLoader;
    private File classOutputDir;
    private String classNameHint;
    private Class<?> inputType;
    private Class<?> outputType;
    /**
     * The input type actually used for {@code parsed.*} proto getter codegen:
     * equals {@link #inputType} for parser-less rules, and {@code null} when a
     * json/yaml/text parser is present (the parser reads the map, so no proto
     * cast is emitted). Exposed so the runtime can route a log to this rule
     * only when the incoming object matches, without re-parsing the DSL. Set
     * by {@link #compileFromModel}.
     */
    private Class<?> effectiveInputType;
    private DslSourceRef sourceRef = DslSourceRef.ofRule(null, DslSourceRef.UNRESOLVED);
    /**
     * Optional content hash threaded into every generated rule's {@code GateHolder}
     * constructor argument. Stamped onto every captured debug record so a UI / CLI
     * session can detect mid-session hot-update boundaries. Defaults to the empty
     * string when callers don't supply one.
     */
    private String content = "";

    // ==================== Parser type detection ====================

    enum ParserType { JSON, YAML, TEXT, NONE }

    static class PrivateMethod {
        final String source;
        final String[][] lvtVars;

        PrivateMethod(final String source, final String[][] lvtVars) {
            this.source = source;
            this.lvtVars = lvtVars;
        }
    }

    static class LocalVarInfo {
        final String javaVarName;
        final Class<?> resolvedType;
        final String descriptor;

        LocalVarInfo(final String javaVarName, final Class<?> resolvedType) {
            this.javaVarName = javaVarName;
            this.resolvedType = resolvedType;
            this.descriptor = "L" + resolvedType.getName().replace('.', '/') + ";";
        }
    }

    static class GenCtx {
        final ParserType parserType;
        final Class<?> inputType;
        final Class<?> outputType;
        final List<PrivateMethod> privateMethods = new ArrayList<>();
        final Map<String, Integer> methodCounts = new HashMap<>();
        /** Rule's name — emitted as the first arg of every probe call site. */
        String ruleName = "";

        // Set by generateExtraLogAccess for primitive optimization in callers.
        // Reset to null by generateValueAccess at the start of each value access.
        Class<?> lastResolvedType;
        String lastNullChecks;
        String lastRawChain;

        // Per-method proto field variable caching (NONE + inputType only).
        // Maps chain key ("response", "response.responseCode") to variable name ("_t0", "_t1").
        // Enables dedup: the same chain accessed multiple times reuses the same variable.
        final Map<String, String> protoVars = new HashMap<>();
        final List<String[]> protoLvtVars = new ArrayList<>();
        final StringBuilder protoVarDecls = new StringBuilder();
        int protoVarCounter;
        boolean usedProtoAccess;

        // Local variables from def statements.
        // Maps user-chosen name (e.g., "metadata") to type info.
        final Map<String, LocalVarInfo> localVars = new HashMap<>();
        final StringBuilder localVarDecls = new StringBuilder();
        final List<String[]> localVarLvtVars = new ArrayList<>();

        GenCtx(final ParserType parserType, final Class<?> inputType,
               final Class<?> outputType) {
            this.parserType = parserType;
            this.inputType = inputType;
            this.outputType = outputType;
        }

        String nextMethodName(final String prefix) {
            final int count = methodCounts.merge(prefix, 1, Integer::sum);
            return count == 1 ? "_" + prefix : "_" + prefix + "_" + count;
        }

        void clearExtraLogResult() {
            lastResolvedType = null;
            lastNullChecks = null;
            lastRawChain = null;
        }

        void resetProtoVars() {
            protoVars.clear();
            protoLvtVars.clear();
            protoVarDecls.setLength(0);
            protoVarCounter = 0;
            usedProtoAccess = false;
            localVars.clear();
            localVarDecls.setLength(0);
            localVarLvtVars.clear();
        }

        Object[] saveProtoVarState() {
            return new Object[]{
                new HashMap<>(protoVars),
                new ArrayList<>(protoLvtVars),
                protoVarDecls.toString(),
                protoVarCounter,
                usedProtoAccess,
                new HashMap<>(localVars),
                localVarDecls.toString(),
                new ArrayList<>(localVarLvtVars)
            };
        }

        @SuppressWarnings("unchecked")
        void restoreProtoVarState(final Object[] state) {
            protoVars.clear();
            protoVars.putAll((Map<String, String>) state[0]);
            protoLvtVars.clear();
            protoLvtVars.addAll((List<String[]>) state[1]);
            protoVarDecls.setLength(0);
            protoVarDecls.append((String) state[2]);
            protoVarCounter = (Integer) state[3];
            usedProtoAccess = (Boolean) state[4];
            localVars.clear();
            localVars.putAll((Map<String, LocalVarInfo>) state[5]);
            localVarDecls.setLength(0);
            localVarDecls.append((String) state[6]);
            localVarLvtVars.clear();
            localVarLvtVars.addAll((List<String[]>) state[7]);
        }
    }

    public LALClassGenerator() {
        this(ClassPool.getDefault(), null);
        classOutputDir = DslGeneratedFileWriter.resolveClassDumpDir("lal");
    }

    public LALClassGenerator(final ClassPool classPool) {
        this(classPool, null);
    }

    /**
     * Runtime-rule constructor: caller supplies the per-file {@link ClassPool} (already scoped
     * to a per-file {@code RuleClassLoader} via {@code LoaderClassPath}) and the target
     * {@link ClassLoader}. Every class this generator emits will be loaded into
     * {@code targetClassLoader} rather than the OAP app loader.
     */
    public LALClassGenerator(final ClassPool classPool, final ClassLoader targetClassLoader) {
        this.classPool = classPool;
        this.targetClassLoader = targetClassLoader;
    }

    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    public void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    public void setInputType(final Class<?> inputType) {
        this.inputType = inputType;
    }

    public void setOutputType(final Class<?> outputType) {
        this.outputType = outputType;
    }

    public Class<?> getEffectiveInputType() {
        return effectiveInputType;
    }

    /**
     * @param ref the rule's coordinate
     */
    public void setSourceRef(final DslSourceRef ref) {
        this.sourceRef = ref == null ? DslSourceRef.ofRule(null, DslSourceRef.UNRESOLVED) : ref;
    }

    @Deprecated
    public void setYamlSource(final String yamlSource) {
        this.sourceRef = DslSourceRef.parse(yamlSource);
    }

    /**
     * Sets the content hash baked into the next compiled rule's
     * {@code GateHolder} constructor argument. Caller-supplied so the
     * runtime-rule hot-update path can pass its already-computed
     * {@code ContentHash} through unchanged. {@code null} → empty string.
     */
    public void setContent(final String content) {
        this.content = content == null ? "" : content;
    }

    /**
     * Builds the class name as {@code {yamlBaseName}_L{lineNo}_{hint}}, falling back to a
     * counter-suffixed default when no hint is set.
     *
     * <p>A non-null {@code targetClassLoader} means the runtime-rule path gave this apply its own
     * loader, so identical names are already isolated and process-wide dedup would only grow
     * without bound — hence the allocation policy, unlike the stem, is decided here.
     *
     * @param defaultPrefix prefix for the counter-based fallback name
     * @return the fully-qualified name to define
     */
    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return DslClassNaming.allocate(
                PACKAGE_PREFIX + DslClassNaming.stem(
                    sourceRef, classNameHint, LALConfigs.LAL_CATALOG),
                targetClassLoader == null);
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    /**
     * Adds the {@code public final GateHolder debug = new GateHolder("...")} instance
     * field and the {@code debugHolder()} accessor on the generated rule class.
     * Mandatory for every compiled rule because {@code execute()} embeds
     * {@code if (this.debug.isGateOn()) LALDebug.captureXxx(this.debug, ...)} call sites
     * that read the field directly.
     */
    private void emitDebugHolderMembers(final CtClass ctClass) throws CannotCompileException {
        if (!DSLDebugCodegenSwitch.isInjectionEnabled()) {
            // Injection off — fall back to the LalExpression.debugHolder() default
            // (null), no GateHolder field, no probe call sites.
            return;
        }
        final String escapedContent = DslJavaSourceText.toLiteral(content);
        // Per-rule capture binding — instance field, lowercase per Java
        // convention (it's a final but not a static-final constant).
        ctClass.addField(CtField.make(
            "public final " + LALCodegenHelper.GATE_HOLDER_FQCN + " debug = new "
                + LALCodegenHelper.GATE_HOLDER_FQCN + "(\"" + escapedContent + "\");",
            ctClass));
        ctClass.addMethod(CtNewMethod.make(
            "public " + LALCodegenHelper.GATE_HOLDER_FQCN + " debugHolder() { return this.debug; }",
            ctClass));
    }

    private static ParserType detectParserType(
            final List<? extends LALScriptModel.FilterStatement> stmts) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.JsonParser) {
                return ParserType.JSON;
            }
            if (stmt instanceof LALScriptModel.YamlParser) {
                return ParserType.YAML;
            }
            if (stmt instanceof LALScriptModel.TextParser) {
                return ParserType.TEXT;
            }
            if (stmt instanceof LALScriptModel.IfBlock) {
                final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
                ParserType t = detectParserType(ifBlock.getThenBranch());
                if (t != ParserType.NONE) {
                    return t;
                }
                t = detectParserType(ifBlock.getElseBranch());
                if (t != ParserType.NONE) {
                    return t;
                }
            }
        }
        return ParserType.NONE;
    }

    // ==================== Compilation ====================

    /**
     * Compiles a LAL DSL script into a LalExpression implementation.
     */
    public LalExpression compile(final String dsl) throws Exception {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        return compileFromModel(model);
    }

    /**
     * Compiles from a pre-parsed model. Generates a single class with
     * execute() and private helper methods.
     */
    public LalExpression compileFromModel(final LALScriptModel model) throws Exception {
        final String className = makeClassName("LalExpr_");
        final ParserType parserType = detectParserType(model.getStatements());
        final Class<?> resolvedOutput = this.outputType != null
            ? this.outputType
            : LogBuilder.class;
        // inputType is only meaningful for parser-less rules (NONE) where parsed.*
        // generates direct proto getter calls.  When a parser is present (json/yaml/text),
        // parsed.* reads from the parsed map and tag() reads from LogData.Builder tags,
        // so inputType must be null to avoid mis-guarding codegen branches.
        this.effectiveInputType =
            parserType == ParserType.NONE ? this.inputType : null;
        final GenCtx genCtx = new GenCtx(parserType, this.effectiveInputType, resolvedOutput);

        if (parserType == ParserType.NONE && this.inputType != null) {
            log.info("LAL rule has no parser — using inputType {} for "
                + "direct getter calls.", this.inputType.getName());
        }

        final String executeBody = generateExecuteMethod(model, genCtx);

        if (log.isDebugEnabled()) {
            log.debug("LAL compile AST: {}", model);
            log.debug("LAL compile execute():\n{}", executeBody);
            for (final PrivateMethod pm : genCtx.privateMethods) {
                log.debug("LAL compile private method:\n{}", pm.source);
            }
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression"));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        // Per-rule capture binding the generated probes read via this.debug.isGateOn().
        // Symmetric with MAL's holder member; see LALCodegenHelper.emitCaptureCall.
        emitDebugHolderMembers(ctClass);

        // Add private methods BEFORE execute so Javassist can resolve calls
        final List<CtMethod> privateMethods = new ArrayList<>();
        for (final PrivateMethod pm : genCtx.privateMethods) {
            final CtMethod ctMethod = CtNewMethod.make(pm.source, ctClass);
            ctClass.addMethod(ctMethod);
            privateMethods.add(ctMethod);
            DslGeneratedFileWriter.addLocalVariableTable(ctMethod, className, pm.lvtVars);
        }

        final CtMethod execMethod = CtNewMethod.make(executeBody, ctClass);
        ctClass.addMethod(execMethod);

        // Build LVT for execute(): params + h + optional _p and proto vars
        final List<String[]> execLvt = new ArrayList<>();
        execLvt.add(new String[]{"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"});
        execLvt.add(new String[]{"ctx", "L" + EXEC_CTX.replace('.', '/') + ";"});
        execLvt.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});
        if (genCtx.usedProtoAccess) {
            if (genCtx.inputType != null) {
                execLvt.add(new String[]{"_p",
                    "L" + genCtx.inputType.getName().replace('.', '/') + ";"});
            }
            execLvt.addAll(genCtx.protoLvtVars);
        }
        execLvt.addAll(genCtx.localVarLvtVars);
        DslGeneratedFileWriter.addLocalVariableTable(execMethod, className,
            execLvt.toArray(new String[0][]));
        // One LineNumberTable entry per method, at its own signature line -- not a per-statement
        // table. The per-statement scan marks boundaries at stores to a result slot, and LAL
        // bodies are largely void invocations that store nothing there, so its numbers came out
        // as statement ordinals (1, 2, 3...) matching no file. The signature line is searched for
        // in the assembled source below, so it survives changes to the envelope.

        // In production NO generated source file exists -- it is written only under
        // SW_DYNAMIC_CLASS_ENGINE_DEBUG -- so naming it would name nothing. The rule file is what
        // an operator can open, and the class name cannot substitute for it: sanitising maps '/',
        // '-' and '.' all to '_', so the path is not recoverable from execution_basic_L110_x.
        final DslSourceRef ref = sourceRef;
        final String sourceText = buildSourceText(ctClass, genCtx, executeBody);
        DslGeneratedFileWriter.setSourceFile(ctClass, ref.sourceFileOf(ctClass.getSimpleName()));
        // One entry at the execute() signature, located in the assembled text so it cannot drift
        // from the envelope -- which varies per rule, as each extractor adds a private method.
        DslGeneratedFileWriter.attachSignatureLine(execMethod,
            DslGeneratedFileWriter.lineOfMethod(sourceText, "execute"));
        // execute() is mostly dispatch: a real LAL failure throws inside _extractor/_sink, so
        // those frames are the ones worth locating. Names are unique per class, so the
        // declaration lookup resolves unambiguously.
        for (final CtMethod pmMethod : privateMethods) {
            DslGeneratedFileWriter.attachSignatureLine(pmMethod,
                DslGeneratedFileWriter.lineOfMethod(sourceText, pmMethod.getName()));
        }

        DslGeneratedFileWriter.writeClassFile(classOutputDir, ctClass);
        DslGeneratedFileWriter.writeSourceFile(classOutputDir, ctClass.getSimpleName(), sourceText);

        final Class<?> clazz = defineClass(ctClass);
        ctClass.detach();
        return (LalExpression) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Assembles the exact Java source handed to Javassist, and returns it rather than writing it:
     * {@link DslGeneratedFileWriter#lineOfMethod} indexes this same text to find each method's
     * signature line. The write happens once beside the {@code .class}, and only under
     * {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG}, so an IDE source-attach renders the exact code
     * Javassist compiled instead of a decompiler's approximation.
     */
    private String buildSourceText(final CtClass ctClass,
                                   final GenCtx genCtx,
                                   final String executeBody) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(ctClass.getPackageName()).append(";\n\n");
        sb.append("public class ").append(ctClass.getSimpleName())
          .append(" implements ")
          .append(LalExpression.class.getName())
          .append(" {\n\n");
        if (DSLDebugCodegenSwitch.isInjectionEnabled()) {
            // Same escapedContent the bytecode emits — the verbatim LAL DSL
            // for this rule, escaped for a Java string literal so the
            // generated source compiles structurally identical to the .class.
            sb.append("    public final ").append(LALCodegenHelper.GATE_HOLDER_FQCN)
              .append(" debug = new ").append(LALCodegenHelper.GATE_HOLDER_FQCN)
              .append("(\"").append(DslJavaSourceText.toLiteral(content))
              .append("\");\n\n");
            sb.append("    public ").append(LALCodegenHelper.GATE_HOLDER_FQCN)
              .append(" debugHolder() { return this.debug; }\n\n");
        }
        for (final PrivateMethod pm : genCtx.privateMethods) {
            sb.append("    ").append(pm.source.replace("\n", "\n    ")).append("\n\n");
        }
        sb.append("    ").append(executeBody.replace("\n", "\n    ")).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Loads a generated class through the configured {@link #targetClassLoader} when set
     * (runtime-rule hot-update path: class lands in the per-file {@code RuleClassLoader}),
     * or via the neighbor-class form when {@code targetClassLoader} is {@code null}
     * (startup path: class lands in the OAP app loader alongside
     * {@link LalExpressionPackageHolder}).
     *
     * <p>Which of the two definition paths applies, and why the
     * {@link BytecodeClassDefiner} one exists at all, is documented on
     * {@link BytecodeClassDefiner#define}.
     */
    private Class<?> defineClass(final CtClass ctClass) throws CannotCompileException {
        return BytecodeClassDefiner.define(ctClass, targetClassLoader, LalExpressionPackageHolder.class);
    }

    private static boolean hasParsedAccess(
            final List<? extends LALScriptModel.FilterStatement> stmts) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.ExtractorBlock) {
                return true;
            }
            if (stmt instanceof LALScriptModel.IfBlock) {
                final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
                if (hasParsedAccess(ifBlock.getThenBranch())
                        || hasParsedAccess(ifBlock.getElseBranch())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Execute method generation ====================

    private String generateExecuteMethod(final LALScriptModel model,
                                          final GenCtx genCtx) {
        genCtx.resetProtoVars();
        genCtx.ruleName = classNameHint == null ? "lal" : classNameHint;

        // Generate body first so proto var declarations are collected
        final StringBuilder bodyContent = new StringBuilder();
        for (final LALScriptModel.FilterStatement stmt : model.getStatements()) {
            generateFilterStatement(bodyContent, stmt, genCtx);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(EXEC_CTX).append(" ctx) {\n");
        sb.append("  ").append(H).append(" h = new ").append(H).append("(ctx);\n");

        // Create the output object and store in ctx before extractor runs.
        // Then bind the typed input + metadata onto the builder eagerly so
        // every downstream probe (extractor / per-statement / sink) sees a
        // builder whose state already reflects the input — the merged
        // tags[] view in outputToJson() is built from logData + lalTags,
        // and would otherwise show only lalTags at the function probes
        // since RecordSinkListener.parse() doesn't fire init() until sink
        // time.
        sb.append("  h.ctx().setOutput(new ")
          .append(genCtx.outputType.getName()).append("());\n");
        sb.append("  h.ctx().outputAsBuilder().bindInput(h.ctx().metadata(), h.ctx().input());\n");

        // Push the rule's debug holder onto ctx so downstream analyzer wrappers
        // (RecordSinkListener.parse, MetricExtractor.submitMetrics) fire their
        // terminal-output probes without re-resolving the rule. Skipped entirely
        // when injection is off — there's no debug field to read.
        if (DSLDebugCodegenSwitch.isInjectionEnabled()) {
            sb.append("  ctx.setDebugHolder(this.debug);\n");
        }

        // Top-of-pipeline capture: raw log body view as the rule first sees it.
        LALCodegenHelper.emitCaptureCall(sb, "Text", genCtx.ruleName, 0, "ctx", "");

        // Insert _p + proto var declarations if any proto field access was used
        if (genCtx.usedProtoAccess) {
            if (genCtx.inputType != null) {
                final String elTypeName = genCtx.inputType.getName();
                sb.append("  ").append(elTypeName).append(" _p = (")
                  .append(elTypeName).append(") h.ctx().input();\n");
            }
            sb.append(genCtx.protoVarDecls);
        }

        // Insert local var declarations from def statements at execute level
        if (genCtx.localVarDecls.length() > 0) {
            sb.append(genCtx.localVarDecls);
        }

        sb.append(bodyContent);
        sb.append("}\n");
        return sb.toString();
    }

    private void generateFilterStatement(final StringBuilder sb,
                                          final LALScriptModel.FilterStatement stmt,
                                          final GenCtx genCtx) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                sb.append("  filterSpec.textWithRegexp(ctx, \"")
                  .append(DslJavaSourceText.toLiteral(tp.getRegexpPattern()))
                  .append("\", ").append(tp.isAbortOnFailure()).append(");\n");
            } else {
                sb.append("  filterSpec.text(ctx);\n");
            }
            LALCodegenHelper.emitCaptureCall(sb, "Parser", genCtx.ruleName, 0, "ctx", "");
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            sb.append("  filterSpec.json(ctx, ")
              .append(((LALScriptModel.JsonParser) stmt).isAbortOnFailure()).append(");\n");
            LALCodegenHelper.emitCaptureCall(sb, "Parser", genCtx.ruleName, 0, "ctx", "");
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            sb.append("  filterSpec.yaml(ctx, ")
              .append(((LALScriptModel.YamlParser) stmt).isAbortOnFailure()).append(");\n");
            LALCodegenHelper.emitCaptureCall(sb, "Parser", genCtx.ruleName, 0, "ctx", "");
        } else if (stmt instanceof LALScriptModel.AbortStatement) {
            sb.append("  filterSpec.abort(ctx);\n");
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            LALBlockCodegen.generateExtractorMethod(
                sb, (LALScriptModel.ExtractorBlock) stmt, genCtx);
            LALCodegenHelper.emitCaptureCall(sb, "Extractor", genCtx.ruleName, 0, "ctx", "");
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink(ctx);\n");
            } else {
                LALBlockCodegen.generateSinkMethod(sb, sink, genCtx);
            }
            // No probe at the sink boundary itself — the terminal capture happens
            // downstream in RecordSinkListener.parse (output record) and
            // MetricExtractor.submitMetrics (output metric), and only fires for
            // records the sink kept. SWIP-13: A7.
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            generateTopLevelIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
        } else if (stmt instanceof LALScriptModel.DefStatement) {
            LALDefCodegen.generateDefStatement(
                sb, (LALScriptModel.DefStatement) stmt, genCtx);
        }
    }

    private void generateTopLevelIfBlock(final StringBuilder sb,
                                          final LALScriptModel.IfBlock ifBlock,
                                          final GenCtx genCtx) {
        sb.append("  if (");
        LALValueCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        for (final LALScriptModel.FilterStatement s : ifBlock.getThenBranch()) {
            generateFilterStatement(sb, s, genCtx);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            for (final LALScriptModel.FilterStatement s : ifBlock.getElseBranch()) {
                generateFilterStatement(sb, s, genCtx);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Source generation (for testing) ====================

    /**
     * Generates the Java source of execute() + private methods for
     * debugging/testing.
     */
    public String generateSource(final String dsl) {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        final Class<?> resolvedOutput = this.outputType != null
            ? this.outputType
            : LogBuilder.class;
        final ParserType pt = detectParserType(model.getStatements());
        final GenCtx genCtx = new GenCtx(
            pt, pt == ParserType.NONE ? this.inputType : null, resolvedOutput);
        final String execute = generateExecuteMethod(model, genCtx);
        if (genCtx.privateMethods.isEmpty()) {
            return execute;
        }
        final StringBuilder all = new StringBuilder(execute);
        for (final PrivateMethod m : genCtx.privateMethods) {
            all.append("\n").append(m.source);
        }
        return all.toString();
    }
}
