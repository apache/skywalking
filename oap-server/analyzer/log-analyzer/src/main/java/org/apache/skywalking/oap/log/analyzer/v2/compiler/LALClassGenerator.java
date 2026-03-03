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
    private static final String EXTRACTOR_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.ExtractorSpec";
    private static final String SAMPLE_BUILDER =
        EXTRACTOR_SPEC + "$SampleBuilder";
    private static final String PROCESS_REGISTRY =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry";

    private static final java.util.Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private File classOutputDir;
    private String classNameHint;
    private String extraLogType;

    // ==================== Parser type detection ====================

    private enum ParserType { JSON, YAML, TEXT, NONE }

    private static class PrivateMethod {
        final String source;
        final String[][] lvtVars;

        PrivateMethod(final String source, final String[][] lvtVars) {
            this.source = source;
            this.lvtVars = lvtVars;
        }
    }

    private static class GenCtx {
        final ParserType parserType;
        final String extraLogType;
        final List<PrivateMethod> privateMethods = new ArrayList<>();
        final Map<String, Integer> methodCounts = new HashMap<>();

        GenCtx(final ParserType parserType, final String extraLogType) {
            this.parserType = parserType;
            this.extraLogType = extraLogType;
        }

        String nextMethodName(final String prefix) {
            final int count = methodCounts.merge(prefix, 1, Integer::sum);
            return count == 1 ? "_" + prefix : "_" + prefix + "_" + count;
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

    public void setExtraLogType(final String extraLogType) {
        this.extraLogType = extraLogType;
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

        if (parserType == ParserType.NONE && this.extraLogType == null) {
            if (hasParsedAccess(model.getStatements())) {
                log.warn("LAL rule accesses parsed fields without a parser and without "
                    + "extraLogType — using runtime reflection, which may impact "
                    + "performance. Declare extraLogType in the LAL config to enable "
                    + "direct proto getter calls.");
            }
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
        addLocalVariableTable(execMethod, className, new String[][]{
            {"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"},
            {"ctx", "L" + EXEC_CTX.replace('.', '/') + ";"},
            {"h", "L" + H.replace('.', '/') + ";"}
        });

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
        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(EXEC_CTX).append(" ctx) {\n");
        sb.append("  ").append(H).append(" h = new ").append(H).append("(ctx);\n");

        for (final LALScriptModel.FilterStatement stmt : model.getStatements()) {
            generateFilterStatement(sb, stmt, genCtx);
        }

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
                  .append(escapeJava(tp.getRegexpPattern())).append("\");\n");
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
            generateExtractorMethod(sb, (LALScriptModel.ExtractorBlock) stmt, genCtx);
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink(ctx);\n");
            } else {
                generateSinkMethod(sb, sink, genCtx);
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            generateTopLevelIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
        }
    }

    private void generateTopLevelIfBlock(final StringBuilder sb,
                                          final LALScriptModel.IfBlock ifBlock,
                                          final GenCtx genCtx) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition(), genCtx);
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

    // ==================== Extractor method generation ====================

    private void generateExtractorMethod(final StringBuilder sb,
                                          final LALScriptModel.ExtractorBlock block,
                                          final GenCtx genCtx) {
        final String methodName = genCtx.nextMethodName("extractor");
        final StringBuilder body = new StringBuilder();
        body.append("private void ").append(methodName).append("(")
            .append(EXTRACTOR_SPEC).append(" _e, ").append(H).append(" h) {\n");

        final List<LALScriptModel.FilterStatement> extractorStmts = new ArrayList<>();
        for (final LALScriptModel.ExtractorStatement es : block.getStatements()) {
            extractorStmts.add((LALScriptModel.FilterStatement) es);
        }
        generateExtractorBody(body, extractorStmts, genCtx);

        body.append("}\n");
        genCtx.privateMethods.add(new PrivateMethod(body.toString(), new String[][]{
            {"_e", "L" + EXTRACTOR_SPEC.replace('.', '/') + ";"},
            {"h", "L" + H.replace('.', '/') + ";"}
        }));

        sb.append("  if (!ctx.shouldAbort()) {\n");
        sb.append("    ").append(methodName).append("(filterSpec.extractor(), h);\n");
        sb.append("  }\n");
    }

    private void generateExtractorBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final GenCtx genCtx) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.FieldAssignment) {
                final LALScriptModel.FieldAssignment field =
                    (LALScriptModel.FieldAssignment) stmt;
                sb.append("  _e.").append(field.getFieldType().name().toLowerCase())
                  .append("(h.ctx(), ");
                generateCastedValueAccess(sb, field.getValue(),
                    field.getCastType(), genCtx);
                if (field.getFormatPattern() != null) {
                    sb.append(", \"")
                      .append(escapeJava(field.getFormatPattern()))
                      .append("\"");
                }
                sb.append(");\n");
            } else if (stmt instanceof LALScriptModel.TagAssignment) {
                generateTagAssignment(sb, (LALScriptModel.TagAssignment) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInExtractor(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.MetricsBlock) {
                generateMetricsInline(sb, (LALScriptModel.MetricsBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.SlowSqlBlock) {
                generateSlowSqlInline(sb, (LALScriptModel.SlowSqlBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.SampledTraceBlock) {
                generateSampledTraceInline(sb,
                    (LALScriptModel.SampledTraceBlock) stmt, genCtx);
            }
        }
    }

    private void generateIfBlockInExtractor(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final GenCtx genCtx) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        generateExtractorBody(sb, ifBlock.getThenBranch(), genCtx);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateExtractorBody(sb, ifBlock.getElseBranch(), genCtx);
            sb.append("  }\n");
        }
    }

    // ==================== Metrics inline ====================

    private void generateMetricsInline(
            final StringBuilder sb,
            final LALScriptModel.MetricsBlock block,
            final GenCtx genCtx) {
        sb.append("  { ").append(SAMPLE_BUILDER).append(" _b = _e.prepareMetrics(h.ctx());\n");
        sb.append("  if (_b != null) {\n");
        if (block.getName() != null) {
            sb.append("  _b.name(\"")
                .append(escapeJava(block.getName())).append("\");\n");
        }
        if (block.getTimestampValue() != null) {
            sb.append("  _b.timestamp(");
            generateCastedValueAccess(sb, block.getTimestampValue(),
                block.getTimestampCast(), genCtx);
            sb.append(");\n");
        }
        if (!block.getLabels().isEmpty()) {
            sb.append("  { java.util.Map _labels = new java.util.LinkedHashMap();\n");
            for (final Map.Entry<String, LALScriptModel.TagValue> entry
                    : block.getLabels().entrySet()) {
                sb.append("    _labels.put(\"")
                    .append(escapeJava(entry.getKey())).append("\", ");
                generateCastedValueAccess(sb, entry.getValue().getValue(),
                    entry.getValue().getCastType(), genCtx);
                sb.append(");\n");
            }
            sb.append("    _b.labels(_labels); }\n");
        }
        if (block.getValue() != null) {
            sb.append("  _b.value(");
            if ("Long".equals(block.getValueCast())) {
                sb.append("(double) h.toLong(");
                generateValueAccess(sb, block.getValue(), genCtx);
                sb.append(")");
            } else if ("Integer".equals(block.getValueCast())) {
                sb.append("(double) h.toInt(");
                generateValueAccess(sb, block.getValue(), genCtx);
                sb.append(")");
            } else {
                if (block.getValue().isNumberLiteral()) {
                    sb.append("(double) ").append(block.getValue().getSegments().get(0));
                } else {
                    sb.append("((Number) ");
                    generateValueAccess(sb, block.getValue(), genCtx);
                    sb.append(").doubleValue()");
                }
            }
            sb.append(");\n");
        }
        sb.append("  _e.submitMetrics(h.ctx(), _b);\n");
        sb.append("  } }\n");
    }

    // ==================== SlowSql inline ====================

    private void generateSlowSqlInline(
            final StringBuilder sb,
            final LALScriptModel.SlowSqlBlock block,
            final GenCtx genCtx) {
        sb.append("  _e.prepareSlowSql(h.ctx());\n");
        if (block.getId() != null) {
            sb.append("  _e.slowSqlSpec().id(h.ctx(), ");
            generateCastedValueAccess(sb, block.getId(), block.getIdCast(), genCtx);
            sb.append(");\n");
        }
        if (block.getStatement() != null) {
            sb.append("  _e.slowSqlSpec().statement(h.ctx(), ");
            generateCastedValueAccess(sb, block.getStatement(),
                block.getStatementCast(), genCtx);
            sb.append(");\n");
        }
        if (block.getLatency() != null) {
            sb.append("  _e.slowSqlSpec().latency(h.ctx(), Long.valueOf(h.toLong(");
            generateValueAccess(sb, block.getLatency(), genCtx);
            sb.append(")));\n");
        }
        sb.append("  _e.submitSlowSql(h.ctx());\n");
    }

    // ==================== SampledTrace inline ====================

    private void generateSampledTraceInline(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceBlock block,
            final GenCtx genCtx) {
        sb.append("  _e.prepareSampledTrace(h.ctx());\n");
        generateSampledTraceBody(sb, block.getStatements(), genCtx);
        sb.append("  _e.submitSampledTrace(h.ctx());\n");
    }

    private void generateSampledTraceBody(
            final StringBuilder sb,
            final List<LALScriptModel.SampledTraceStatement> stmts,
            final GenCtx genCtx) {
        for (final LALScriptModel.SampledTraceStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb, (LALScriptModel.SampledTraceField) stmt,
                    genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    private void generateSampledTraceField(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceField field,
            final GenCtx genCtx) {
        switch (field.getFieldType()) {
            case LATENCY:
                sb.append("  _e.sampledTraceSpec().latency(h.ctx(), Long.valueOf(h.toLong(");
                generateValueAccess(sb, field.getValue(), genCtx);
                sb.append(")));\n");
                return;
            case COMPONENT_ID:
                sb.append("  _e.sampledTraceSpec().componentId(h.ctx(), h.toInt(");
                generateValueAccess(sb, field.getValue(), genCtx);
                sb.append("));\n");
                return;
            case URI:
                sb.append("  _e.sampledTraceSpec().uri(h.ctx(), ");
                break;
            case REASON:
                sb.append("  _e.sampledTraceSpec().reason(h.ctx(), ");
                break;
            case PROCESS_ID:
                sb.append("  _e.sampledTraceSpec().processId(h.ctx(), ");
                break;
            case DEST_PROCESS_ID:
                sb.append("  _e.sampledTraceSpec().destProcessId(h.ctx(), ");
                break;
            case DETECT_POINT:
                sb.append("  _e.sampledTraceSpec().detectPoint(h.ctx(), ");
                break;
            case REPORT_SERVICE:
                sb.append("  _e.sampledTraceSpec().")
                  .append(field.getFieldType().name().toLowerCase())
                  .append("(h.ctx(), ");
                break;
            default:
                return;
        }
        generateCastedValueAccess(sb, field.getValue(), field.getCastType(), genCtx);
        sb.append(");\n");
    }

    private void generateSampledTraceIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final GenCtx genCtx) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getThenBranch(), genCtx);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getElseBranch(), genCtx);
            sb.append("  }\n");
        }
    }

    private void generateSampledTraceBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final GenCtx genCtx) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb,
                    (LALScriptModel.SampledTraceField) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.FieldAssignment) {
                generateSampledTraceFieldFromAssignment(sb,
                    (LALScriptModel.FieldAssignment) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    private void generateSampledTraceFieldFromAssignment(
            final StringBuilder sb,
            final LALScriptModel.FieldAssignment fa,
            final GenCtx genCtx) {
        switch (fa.getFieldType()) {
            case TIMESTAMP:
                sb.append("  _e.sampledTraceSpec().latency(h.ctx(), Long.valueOf(h.toLong(");
                generateValueAccess(sb, fa.getValue(), genCtx);
                sb.append(")));\n");
                break;
            default:
                sb.append("  _e.sampledTraceSpec().")
                  .append(fa.getFieldType().name().toLowerCase())
                  .append("(h.ctx(), ");
                generateCastedValueAccess(sb, fa.getValue(), fa.getCastType(), genCtx);
                sb.append(");\n");
                break;
        }
    }

    // ==================== Tag assignment ====================

    private void generateTagAssignment(final StringBuilder sb,
                                         final LALScriptModel.TagAssignment tag,
                                         final GenCtx genCtx) {
        final Map<String, LALScriptModel.TagValue> tags = tag.getTags();
        if (tags.isEmpty()) {
            return;
        }
        if (tags.size() == 1) {
            final Map.Entry<String, LALScriptModel.TagValue> entry =
                tags.entrySet().iterator().next();
            sb.append("  _e.tag(h.ctx(), java.util.Collections.singletonMap(\"")
              .append(escapeJava(entry.getKey())).append("\", ");
            generateCastedValueAccess(sb, entry.getValue().getValue(),
                entry.getValue().getCastType(), genCtx);
            sb.append("));\n");
        } else {
            sb.append("  { java.util.Map _tagMap = new java.util.LinkedHashMap();\n");
            for (final Map.Entry<String, LALScriptModel.TagValue> entry
                    : tags.entrySet()) {
                sb.append("    _tagMap.put(\"")
                  .append(escapeJava(entry.getKey())).append("\", ");
                generateCastedValueAccess(sb, entry.getValue().getValue(),
                    entry.getValue().getCastType(), genCtx);
                sb.append(");\n");
            }
            sb.append("    _e.tag(h.ctx(), _tagMap); }\n");
        }
    }

    // ==================== Sink method generation ====================

    private void generateSinkMethod(final StringBuilder sb,
                                     final LALScriptModel.SinkBlock sink,
                                     final GenCtx genCtx) {
        final String methodName = genCtx.nextMethodName("sink");
        final StringBuilder body = new StringBuilder();
        body.append("private void ").append(methodName).append("(")
            .append(FILTER_SPEC).append(" _f, ").append(H).append(" h) {\n");

        final List<LALScriptModel.FilterStatement> sinkStmts = new ArrayList<>();
        for (final LALScriptModel.SinkStatement ss : sink.getStatements()) {
            sinkStmts.add((LALScriptModel.FilterStatement) ss);
        }
        generateSinkBody(body, sinkStmts, genCtx);

        body.append("}\n");
        genCtx.privateMethods.add(new PrivateMethod(body.toString(), new String[][]{
            {"_f", "L" + FILTER_SPEC.replace('.', '/') + ";"},
            {"h", "L" + H.replace('.', '/') + ";"}
        }));

        sb.append("  if (!ctx.shouldAbort()) {\n");
        sb.append("    ").append(methodName).append("(filterSpec, h);\n");
        sb.append("  }\n");
        sb.append("  filterSpec.finalizeSink(ctx);\n");
    }

    private void generateSinkBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final GenCtx genCtx) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.EnforcerStatement) {
                sb.append("  _f.enforcer(h.ctx());\n");
            } else if (stmt instanceof LALScriptModel.DropperStatement) {
                sb.append("  _f.dropper(h.ctx());\n");
            } else if (stmt instanceof LALScriptModel.SamplerBlock) {
                generateSamplerInline(sb, (LALScriptModel.SamplerBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInSink(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    private void generateIfBlockInSink(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final GenCtx genCtx) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        generateSinkBody(sb, ifBlock.getThenBranch(), genCtx);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSinkBody(sb, ifBlock.getElseBranch(), genCtx);
            sb.append("  }\n");
        }
    }

    // ==================== Sampler/RateLimit inline ====================

    private void generateSamplerInline(
            final StringBuilder sb,
            final LALScriptModel.SamplerBlock block,
            final GenCtx genCtx) {
        generateSamplerContents(sb, block.getContents(), genCtx);
    }

    private void generateSamplerContents(
            final StringBuilder sb,
            final List<LALScriptModel.SamplerContent> contents,
            final GenCtx genCtx) {
        for (final LALScriptModel.SamplerContent content : contents) {
            if (content instanceof LALScriptModel.RateLimitBlock) {
                generateRateLimitInline(sb, (LALScriptModel.RateLimitBlock) content,
                    genCtx);
            } else if (content instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) content, genCtx);
            }
        }
    }

    private void generateSamplerIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final GenCtx genCtx) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        generateSamplerContentsFromFilterStmts(sb, ifBlock.getThenBranch(), genCtx);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSamplerContentsFromFilterStmts(sb, ifBlock.getElseBranch(), genCtx);
            sb.append("  }\n");
        }
    }

    private void generateSamplerContentsFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final GenCtx genCtx) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SamplerBlock) {
                generateSamplerContents(sb,
                    ((LALScriptModel.SamplerBlock) stmt).getContents(), genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    private void generateRateLimitInline(
            final StringBuilder sb,
            final LALScriptModel.RateLimitBlock block,
            final GenCtx genCtx) {
        sb.append("  _f.sampler().rateLimit(h.ctx(), ");
        if (block.isIdInterpolated()) {
            sb.append("\"\"");
            for (final LALScriptModel.InterpolationPart part : block.getIdParts()) {
                sb.append(" + ");
                if (part.isLiteral()) {
                    sb.append("\"").append(escapeJava(part.getLiteral())).append("\"");
                } else {
                    sb.append("String.valueOf(");
                    generateValueAccess(sb, part.getExpression(), genCtx);
                    sb.append(")");
                }
            }
        } else {
            sb.append("\"").append(escapeJava(block.getId())).append("\"");
        }
        sb.append(", ").append(block.getRpm()).append(");\n");
    }

    // ==================== Conditions ====================

    private void generateCondition(final StringBuilder sb,
                                    final LALScriptModel.Condition cond,
                                    final GenCtx genCtx) {
        if (cond instanceof LALScriptModel.ComparisonCondition) {
            final LALScriptModel.ComparisonCondition cc =
                (LALScriptModel.ComparisonCondition) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight(), genCtx);
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight(), genCtx);
                    sb.append(")");
                    break;
                case GT:
                    sb.append("h.toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null, genCtx);
                    sb.append(") > ");
                    generateConditionValueNumeric(sb, cc.getRight(), genCtx);
                    break;
                case LT:
                    sb.append("h.toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null, genCtx);
                    sb.append(") < ");
                    generateConditionValueNumeric(sb, cc.getRight(), genCtx);
                    break;
                case GTE:
                    sb.append("h.toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null, genCtx);
                    sb.append(") >= ");
                    generateConditionValueNumeric(sb, cc.getRight(), genCtx);
                    break;
                case LTE:
                    sb.append("h.toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null, genCtx);
                    sb.append(") <= ");
                    generateConditionValueNumeric(sb, cc.getRight(), genCtx);
                    break;
                default:
                    break;
            }
        } else if (cond instanceof LALScriptModel.LogicalCondition) {
            final LALScriptModel.LogicalCondition lc =
                (LALScriptModel.LogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft(), genCtx);
            sb.append(lc.getOp() == LALScriptModel.LogicalOp.AND
                ? " && " : " || ");
            generateCondition(sb, lc.getRight(), genCtx);
            sb.append(")");
        } else if (cond instanceof LALScriptModel.NotCondition) {
            sb.append("!(");
            generateCondition(sb,
                ((LALScriptModel.NotCondition) cond).getInner(), genCtx);
            sb.append(")");
        } else if (cond instanceof LALScriptModel.ExprCondition) {
            final String ct = ((LALScriptModel.ExprCondition) cond).getCastType();
            final String method = "Boolean".equals(ct) || "boolean".equals(ct)
                ? ".isTrue(" : ".isNotEmpty(";
            sb.append("h").append(method);
            generateValueAccessObj(sb,
                ((LALScriptModel.ExprCondition) cond).getExpr(),
                ct, genCtx);
            sb.append(")");
        }
    }

    private void generateConditionValue(final StringBuilder sb,
                                         final LALScriptModel.ConditionValue cv,
                                         final GenCtx genCtx) {
        if (cv instanceof LALScriptModel.StringConditionValue) {
            sb.append('"')
              .append(escapeJava(
                  ((LALScriptModel.StringConditionValue) cv).getValue()))
              .append('"');
        } else if (cv instanceof LALScriptModel.NumberConditionValue) {
            final double val =
                ((LALScriptModel.NumberConditionValue) cv).getValue();
            sb.append("Long.valueOf(").append((long) val).append("L)");
        } else if (cv instanceof LALScriptModel.BoolConditionValue) {
            sb.append("Boolean.valueOf(")
              .append(((LALScriptModel.BoolConditionValue) cv).isValue())
              .append(")");
        } else if (cv instanceof LALScriptModel.NullConditionValue) {
            sb.append("null");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            generateValueAccessObj(sb,
                ((LALScriptModel.ValueAccessConditionValue) cv).getValue(),
                null, genCtx);
        }
    }

    private void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv,
            final GenCtx genCtx) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            sb.append((long) ((LALScriptModel.NumberConditionValue) cv)
                .getValue()).append("L");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            sb.append("h.toLong(");
            generateValueAccessObj(sb,
                ((LALScriptModel.ValueAccessConditionValue) cv).getValue(),
                null, genCtx);
            sb.append(")");
        } else {
            sb.append("0L");
        }
    }

    // ==================== Value access ====================

    private void generateCastedValueAccess(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final String castType,
                                            final GenCtx genCtx) {
        if ("String".equals(castType)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append("h.toLong(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else if ("Integer".equals(castType)) {
            sb.append("h.toInt(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else if ("Boolean".equals(castType)) {
            sb.append("h.toBool(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    private void generateValueAccessObj(final StringBuilder sb,
                                         final LALScriptModel.ValueAccess value,
                                         final String castType,
                                         final GenCtx genCtx) {
        if ("String".equals(castType)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    private void generateValueAccess(final StringBuilder sb,
                                      final LALScriptModel.ValueAccess value,
                                      final GenCtx genCtx) {
        // Handle function call primaries (e.g., tag("LOG_KIND"))
        if (value.getFunctionCallName() != null) {
            if ("tag".equals(value.getFunctionCallName())
                    && !value.getFunctionCallArgs().isEmpty()) {
                sb.append("h.tagValue(\"");
                final String key = value.getFunctionCallArgs().get(0)
                    .getValue().getSegments().get(0);
                sb.append(escapeJava(key)).append("\")");
            } else {
                sb.append("null");
            }
            return;
        }

        // Handle string/number literals
        if (value.isStringLiteral() && value.getChain().isEmpty()) {
            sb.append("\"").append(escapeJava(value.getSegments().get(0)))
              .append("\"");
            return;
        }
        if (value.isNumberLiteral() && value.getChain().isEmpty()) {
            final String num = value.getSegments().get(0);
            if (num.contains(".")) {
                sb.append("Double.valueOf(").append(num).append(")");
            } else {
                sb.append("Integer.valueOf(").append(num).append(")");
            }
            return;
        }

        // Handle ProcessRegistry static calls
        if (value.isProcessRegistryRef()) {
            generateProcessRegistryCall(sb, value, genCtx);
            return;
        }

        final List<LALScriptModel.ValueAccessSegment> chain = value.getChain();

        // Handle log.X.Y direct proto getter chains
        if (value.isLogRef()) {
            generateLogAccess(sb, chain);
            return;
        }

        // Handle parsed.X.Y with compile-time type analysis
        if (value.isParsedRef()) {
            generateParsedAccess(sb, chain, genCtx);
            return;
        }

        // Fallback for unknown primary
        if (chain.isEmpty()) {
            sb.append("null");
            return;
        }
        // Treat as parsed ref
        generateParsedAccess(sb, chain, genCtx);
    }

    // ==================== Log access (direct proto getters) ====================

    private static final Map<String, String> LOG_GETTERS = new HashMap<>();
    private static final Map<String, String> TRACE_CONTEXT_GETTERS = new HashMap<>();
    private static final java.util.Set<String> LONG_FIELDS = new java.util.HashSet<>();
    private static final java.util.Set<String> INT_FIELDS = new java.util.HashSet<>();

    static {
        LOG_GETTERS.put("service", "getService");
        LOG_GETTERS.put("serviceInstance", "getServiceInstance");
        LOG_GETTERS.put("endpoint", "getEndpoint");
        LOG_GETTERS.put("timestamp", "getTimestamp");
        LOG_GETTERS.put("body", "getBody");
        LOG_GETTERS.put("traceContext", "getTraceContext");
        LOG_GETTERS.put("tags", "getTags");
        LOG_GETTERS.put("layer", "getLayer");

        TRACE_CONTEXT_GETTERS.put("traceId", "getTraceId");
        TRACE_CONTEXT_GETTERS.put("traceSegmentId", "getTraceSegmentId");
        TRACE_CONTEXT_GETTERS.put("spanId", "getSpanId");

        LONG_FIELDS.add("timestamp");
        INT_FIELDS.add("spanId");
    }

    private void generateLogAccess(final StringBuilder sb,
                                    final List<LALScriptModel.ValueAccessSegment> chain) {
        if (chain.isEmpty()) {
            sb.append("h.ctx().log()");
            return;
        }

        String current = "h.ctx().log()";
        boolean needsBoxing = false;
        String boxType = null;

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                final String name = ((LALScriptModel.FieldSegment) seg).getName();
                if (i == 0 && LOG_GETTERS.containsKey(name)) {
                    if ("traceContext".equals(name)) {
                        current = current + ".getTraceContext()";
                    } else {
                        current = current + "." + LOG_GETTERS.get(name) + "()";
                        if (LONG_FIELDS.contains(name)) {
                            needsBoxing = true;
                            boxType = "Long";
                        }
                    }
                } else if (i == 1 && current.endsWith(".getTraceContext()")
                        && TRACE_CONTEXT_GETTERS.containsKey(name)) {
                    current = current + "." + TRACE_CONTEXT_GETTERS.get(name) + "()";
                    if (INT_FIELDS.contains(name)) {
                        needsBoxing = true;
                        boxType = "Integer";
                    }
                } else {
                    // Fall back to getAt for unknown sub-fields
                    current = H + ".getAt(" + current + ", \""
                        + escapeJava(name) + "\")";
                }
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                current = appendMethodSegment(current, (LALScriptModel.MethodSegment) seg);
            }
        }

        if (needsBoxing) {
            sb.append(boxType).append(".valueOf(").append(current).append(")");
        } else {
            sb.append(current);
        }
    }

    // ==================== Parsed access (compile-time typed) ====================

    private void generateParsedAccess(
            final StringBuilder sb,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final GenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append("h.ctx().parsed()");
            return;
        }

        // Collect leading field segments
        final List<String> fieldKeys = new ArrayList<>();
        int methodStart = -1;
        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                fieldKeys.add(((LALScriptModel.FieldSegment) seg).getName());
            } else {
                methodStart = i;
                break;
            }
        }

        String current;
        switch (genCtx.parserType) {
            case JSON:
            case YAML:
                current = generateMapValCall(fieldKeys);
                break;
            case TEXT:
                if (!fieldKeys.isEmpty()) {
                    current = "h.group(\"" + escapeJava(fieldKeys.get(0)) + "\")";
                } else {
                    current = "h.ctx().parsed()";
                }
                break;
            case NONE:
                if (genCtx.extraLogType != null) {
                    current = generateExtraLogAccess(fieldKeys, genCtx.extraLogType);
                } else {
                    // Fallback to runtime getAt
                    current = "h.ctx().parsed()";
                    for (final String key : fieldKeys) {
                        current = H + ".getAt(" + current + ", \""
                            + escapeJava(key) + "\")";
                    }
                }
                break;
            default:
                current = "null";
                break;
        }

        // Apply remaining method segments
        if (methodStart >= 0) {
            for (int i = methodStart; i < chain.size(); i++) {
                final LALScriptModel.ValueAccessSegment seg = chain.get(i);
                if (seg instanceof LALScriptModel.MethodSegment) {
                    current = appendMethodSegment(current,
                        (LALScriptModel.MethodSegment) seg);
                } else if (seg instanceof LALScriptModel.FieldSegment) {
                    final String name =
                        ((LALScriptModel.FieldSegment) seg).getName();
                    current = H + ".getAt(" + current + ", \""
                        + escapeJava(name) + "\")";
                }
            }
        }

        sb.append(current);
    }

    private String generateMapValCall(final List<String> keys) {
        if (keys.isEmpty()) {
            return "h.ctx().parsed()";
        }
        final StringBuilder call = new StringBuilder("h.mapVal(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append("\"").append(escapeJava(keys.get(i))).append("\"");
        }
        call.append(")");
        return call.toString();
    }

    private String generateExtraLogAccess(final List<String> fieldKeys,
                                           final String extraLogTypeName) {
        if (fieldKeys.isEmpty()) {
            return "h.ctx().extraLog()";
        }

        try {
            final Class<?> rootType = Class.forName(extraLogTypeName);
            final StringBuilder accessSb = new StringBuilder();
            accessSb.append("((").append(extraLogTypeName)
                     .append(") h.ctx().extraLog())");

            Class<?> currentType = rootType;
            boolean lastIsPrimitive = false;
            String lastPrimitiveType = null;

            for (final String field : fieldKeys) {
                final String getterName = "get" + Character.toUpperCase(field.charAt(0))
                    + field.substring(1);
                try {
                    final java.lang.reflect.Method getter =
                        currentType.getMethod(getterName);
                    accessSb.append(".").append(getterName).append("()");
                    currentType = getter.getReturnType();
                    lastIsPrimitive = currentType.isPrimitive();
                    if (lastIsPrimitive) {
                        if (currentType == long.class) {
                            lastPrimitiveType = "Long";
                        } else if (currentType == int.class) {
                            lastPrimitiveType = "Integer";
                        } else if (currentType == boolean.class) {
                            lastPrimitiveType = "Boolean";
                        } else if (currentType == double.class) {
                            lastPrimitiveType = "Double";
                        } else if (currentType == float.class) {
                            lastPrimitiveType = "Float";
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Try hasXxx for protobuf wrapper presence check
                    log.warn("Cannot resolve getter {}.{}() — falling back to "
                        + "runtime reflection", currentType.getSimpleName(), getterName);
                    // Fallback: wrap what we have so far in getAt calls
                    String current = accessSb.toString();
                    for (int i = fieldKeys.indexOf(field); i < fieldKeys.size(); i++) {
                        current = H + ".getAt(" + current + ", \""
                            + escapeJava(fieldKeys.get(i)) + "\")";
                    }
                    return current;
                }
            }

            if (lastIsPrimitive && lastPrimitiveType != null) {
                return lastPrimitiveType + ".valueOf(" + accessSb.toString() + ")";
            }
            return accessSb.toString();
        } catch (ClassNotFoundException e) {
            log.warn("Cannot load extraLogType class '{}' — falling back to "
                + "runtime reflection", extraLogTypeName);
            String current = "h.ctx().parsed()";
            for (final String key : fieldKeys) {
                current = H + ".getAt(" + current + ", \""
                    + escapeJava(key) + "\")";
            }
            return current;
        }
    }

    private String appendMethodSegment(final String current,
                                        final LALScriptModel.MethodSegment ms) {
        if (ms.isSafeNav()) {
            final String mn = ms.getName();
            if ("toString".equals(mn)) {
                return "h.toString(" + current + ")";
            } else if ("trim".equals(mn)) {
                return "h.trim(" + current + ")";
            } else {
                throw new IllegalArgumentException(
                    "Unsupported safe-nav method: ?." + mn + "()");
            }
        } else {
            if (ms.getArguments().isEmpty()) {
                return current + "." + ms.getName() + "()";
            } else {
                return current + "." + ms.getName() + "("
                    + generateMethodArgs(ms.getArguments()) + ")";
            }
        }
    }

    // ==================== ProcessRegistry ====================

    private void generateProcessRegistryCall(
            final StringBuilder sb,
            final LALScriptModel.ValueAccess value,
            final GenCtx genCtx) {
        final List<LALScriptModel.ValueAccessSegment> chain = value.getChain();
        if (chain.isEmpty()) {
            sb.append("null");
            return;
        }
        final LALScriptModel.ValueAccessSegment seg = chain.get(0);
        if (seg instanceof LALScriptModel.MethodSegment) {
            final LALScriptModel.MethodSegment ms =
                (LALScriptModel.MethodSegment) seg;
            sb.append(PROCESS_REGISTRY).append(".")
              .append(ms.getName()).append("(");
            final List<LALScriptModel.FunctionArg> args = ms.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateCastedValueAccess(sb,
                    args.get(i).getValue(), args.get(i).getCastType(), genCtx);
            }
            sb.append(")");
        } else {
            sb.append("null");
        }
    }

    private String generateMethodArgs(
            final List<LALScriptModel.FunctionArg> args) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final LALScriptModel.FunctionArg arg = args.get(i);
            final LALScriptModel.ValueAccess va = arg.getValue();
            if (va.isStringLiteral()) {
                sb.append("\"").append(escapeJava(va.getSegments().get(0))).append("\"");
            } else if (va.isNumberLiteral()) {
                sb.append(va.getSegments().get(0));
            } else {
                sb.append("null");
            }
        }
        return sb.toString();
    }

    // ==================== Utilities ====================

    private static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

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
