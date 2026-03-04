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
    private Class<?> extraLogType;

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

    static class GenCtx {
        final ParserType parserType;
        final Class<?> extraLogType;
        final List<PrivateMethod> privateMethods = new ArrayList<>();
        final Map<String, Integer> methodCounts = new HashMap<>();

        // Set by generateExtraLogAccess for primitive optimization in callers.
        // Reset to null by generateValueAccess at the start of each value access.
        Class<?> lastResolvedType;
        String lastNullChecks;
        String lastRawChain;

        // Per-method proto field variable caching (NONE + extraLogType only).
        // Maps chain key ("response", "response.responseCode") to variable name ("_t0", "_t1").
        // Enables dedup: the same chain accessed multiple times reuses the same variable.
        final Map<String, String> protoVars = new HashMap<>();
        final List<String[]> protoLvtVars = new ArrayList<>();
        final StringBuilder protoVarDecls = new StringBuilder();
        int protoVarCounter;
        boolean usedProtoAccess;

        GenCtx(final ParserType parserType, final Class<?> extraLogType) {
            this.parserType = parserType;
            this.extraLogType = extraLogType;
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
        }

        Object[] saveProtoVarState() {
            return new Object[]{
                new HashMap<>(protoVars),
                new ArrayList<>(protoLvtVars),
                protoVarDecls.toString(),
                protoVarCounter,
                usedProtoAccess
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
        }
    }

    public LALClassGenerator() {
        this(ClassPool.getDefault());
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

    public void setExtraLogType(final Class<?> extraLogType) {
        this.extraLogType = extraLogType;
    }

    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(
                PACKAGE_PREFIX + LALCodegenHelper.sanitizeName(classNameHint));
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
        final GenCtx genCtx = new GenCtx(parserType, this.extraLogType);

        if (parserType == ParserType.NONE && this.extraLogType != null) {
            log.info("LAL rule has no parser — using extraLogType {} for "
                + "direct getter calls.", this.extraLogType.getName());
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
        }

        final javassist.CtMethod execMethod = CtNewMethod.make(executeBody, ctClass);
        ctClass.addMethod(execMethod);

        // Build LVT for execute(): params + h + optional _p and proto vars
        final List<String[]> execLvt = new ArrayList<>();
        execLvt.add(new String[]{"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"});
        execLvt.add(new String[]{"ctx", "L" + EXEC_CTX.replace('.', '/') + ";"});
        execLvt.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});
        if (genCtx.usedProtoAccess && genCtx.extraLogType != null) {
            execLvt.add(new String[]{"_p",
                "L" + genCtx.extraLogType.getName().replace('.', '/') + ";"});
            execLvt.addAll(genCtx.protoLvtVars);
        }
        addLocalVariableTable(execMethod, className,
            execLvt.toArray(new String[0][]));

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

        // Insert _p + proto var declarations if any proto field access was used
        if (genCtx.usedProtoAccess && genCtx.extraLogType != null) {
            final String elTypeName = genCtx.extraLogType.getName();
            sb.append("  ").append(elTypeName).append(" _p = (")
              .append(elTypeName).append(") h.ctx().extraLog();\n");
            sb.append(genCtx.protoVarDecls);
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
        }
    }

    private void generateTopLevelIfBlock(final StringBuilder sb,
                                          final LALScriptModel.IfBlock ifBlock,
                                          final GenCtx genCtx) {
        sb.append("  if (");
        LALBlockCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
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
        final GenCtx genCtx = new GenCtx(
            detectParserType(model.getStatements()), this.extraLogType);
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
