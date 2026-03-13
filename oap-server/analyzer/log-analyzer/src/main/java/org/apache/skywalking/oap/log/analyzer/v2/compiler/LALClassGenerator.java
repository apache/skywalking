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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.library.util.StringUtil;

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

    private static final java.util.Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private File classOutputDir;
    private String classNameHint;
    private Class<?> inputType;
    private Class<?> outputType;
    private String yamlSource;

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
        this(ClassPool.getDefault());
        if (StringUtil.isNotEmpty(System.getenv("SW_DYNAMIC_CLASS_ENGINE_DEBUG"))) {
            classOutputDir = new File(WorkPath.getPath().getParentFile(), "lal-rt");
        }
    }

    public LALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
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

    public void setYamlSource(final String yamlSource) {
        this.yamlSource = yamlSource;
    }

    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(PACKAGE_PREFIX + buildHintedName());
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    /**
     * Builds class name from {@code yamlSource} + {@code classNameHint}.
     * Pattern: {@code {yamlBaseName}_L{lineNo}_{hint}} when yamlSource is available,
     * falls back to just {@code {hint}} otherwise.
     */
    private String buildHintedName() {
        final String hint = LALCodegenHelper.sanitizeName(classNameHint);
        if (yamlSource == null) {
            return hint;
        }
        String yamlBase = yamlSource;
        String lineNo = null;
        final int colonIdx = yamlSource.lastIndexOf(':');
        if (colonIdx > 0) {
            yamlBase = yamlSource.substring(0, colonIdx);
            lineNo = yamlSource.substring(colonIdx + 1);
        }
        final int dotIdx = yamlBase.lastIndexOf('.');
        if (dotIdx > 0) {
            yamlBase = yamlBase.substring(0, dotIdx);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(LALCodegenHelper.sanitizeName(yamlBase));
        if (lineNo != null) {
            sb.append("_L").append(lineNo);
        }
        sb.append('_').append(hint);
        return sb.toString();
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

    /**
     * Adds a {@code LineNumberTable} attribute by scanning bytecode for
     * store instructions to local variable slots &ge; {@code firstResultSlot}.
     */
    private void addLineNumberTable(final javassist.CtMethod method,
                                    final int firstResultSlot) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }

            final List<int[]> entries = new ArrayList<>();
            int line = 1;
            boolean nextIsNewLine = true;

            final javassist.bytecode.CodeIterator ci = code.iterator();
            while (ci.hasNext()) {
                final int pc = ci.next();
                if (nextIsNewLine) {
                    entries.add(new int[]{pc, line++});
                    nextIsNewLine = false;
                }
                final int op = ci.byteAt(pc) & 0xFF;
                int slot = -1;
                if (op >= 59 && op <= 78) {
                    slot = (op - 59) % 4;
                } else if (op >= 54 && op <= 58) {
                    slot = ci.byteAt(pc + 1) & 0xFF;
                }
                if (slot >= firstResultSlot) {
                    nextIsNewLine = true;
                }
            }

            if (entries.isEmpty()) {
                return;
            }

            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final byte[] info = new byte[2 + entries.size() * 4];
            info[0] = (byte) (entries.size() >> 8);
            info[1] = (byte) entries.size();
            for (int i = 0; i < entries.size(); i++) {
                final int off = 2 + i * 4;
                info[off] = (byte) (entries.get(i)[0] >> 8);
                info[off + 1] = (byte) entries.get(i)[0];
                info[off + 2] = (byte) (entries.get(i)[1] >> 8);
                info[off + 3] = (byte) entries.get(i)[1];
            }
            code.getAttributes().add(
                new javassist.bytecode.AttributeInfo(cp, "LineNumberTable", info));
        } catch (Exception e) {
            log.warn("Failed to add LineNumberTable: {}", e.getMessage());
        }
    }

    private static void setSourceFile(final CtClass ctClass, final String name) {
        try {
            final javassist.bytecode.ClassFile cf = ctClass.getClassFile();
            final javassist.bytecode.AttributeInfo sf = cf.getAttribute("SourceFile");
            if (sf != null) {
                final javassist.bytecode.ConstPool cp = cf.getConstPool();
                final int idx = cp.addUtf8Info(name);
                sf.set(new byte[]{(byte) (idx >> 8), (byte) idx});
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    /**
     * Builds the SourceFile name for a generated class. When YAML source info
     * is available, produces {@code "default(ruleName.java)"};
     * otherwise falls back to {@code "ruleName.java"}.
     */
    private String formatSourceFileName(final String ruleName) {
        final String classFile = ruleName + ".java";
        if (yamlSource != null) {
            return "(" + yamlSource + ")" + classFile;
        }
        return classFile;
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
            : org.apache.skywalking.oap.server.core.source.LogBuilder.class;
        // inputType is only meaningful for parser-less rules (NONE) where parsed.*
        // generates direct proto getter calls.  When a parser is present (json/yaml/text),
        // parsed.* reads from the parsed map and tag() reads from LogData.Builder tags,
        // so inputType must be null to avoid mis-guarding codegen branches.
        final Class<?> effectiveInputType =
            parserType == ParserType.NONE ? this.inputType : null;
        final GenCtx genCtx = new GenCtx(parserType, effectiveInputType, resolvedOutput);

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

        // Add private methods BEFORE execute so Javassist can resolve calls
        for (final PrivateMethod pm : genCtx.privateMethods) {
            final javassist.CtMethod ctMethod = CtNewMethod.make(pm.source, ctClass);
            ctClass.addMethod(ctMethod);
            addLocalVariableTable(ctMethod, className, pm.lvtVars);
            addLineNumberTable(ctMethod, pm.lvtVars.length + 1); // after this + params
        }

        final javassist.CtMethod execMethod = CtNewMethod.make(executeBody, ctClass);
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
        addLocalVariableTable(execMethod, className,
            execLvt.toArray(new String[0][]));
        addLineNumberTable(execMethod, 3); // slot 0=this, 1=filterSpec, 2=ctx

        setSourceFile(ctClass, formatSourceFileName(
            classNameHint != null ? classNameHint : className));

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        return (LalExpression) clazz.getDeclaredConstructor().newInstance();
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

        // Generate body first so proto var declarations are collected
        final StringBuilder bodyContent = new StringBuilder();
        for (final LALScriptModel.FilterStatement stmt : model.getStatements()) {
            generateFilterStatement(bodyContent, stmt, genCtx);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(EXEC_CTX).append(" ctx) {\n");
        sb.append("  ").append(H).append(" h = new ").append(H).append("(ctx);\n");

        // Create the output object and store in ctx before extractor runs
        sb.append("  h.ctx().setOutput(new ")
          .append(genCtx.outputType.getName()).append("());\n");

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
                  .append(LALCodegenHelper.escapeJava(tp.getRegexpPattern()))
                  .append("\");\n");
            } else {
                sb.append("  filterSpec.text(ctx);\n");
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            sb.append("  filterSpec.json(ctx);\n");
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            sb.append("  filterSpec.yaml(ctx);\n");
        } else if (stmt instanceof LALScriptModel.AbortStatement) {
            sb.append("  filterSpec.abort(ctx);\n");
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            LALBlockCodegen.generateExtractorMethod(
                sb, (LALScriptModel.ExtractorBlock) stmt, genCtx);
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink(ctx);\n");
            } else {
                LALBlockCodegen.generateSinkMethod(sb, sink, genCtx);
            }
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
            : org.apache.skywalking.oap.server.core.source.LogBuilder.class;
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
