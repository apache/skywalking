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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalExpressionPackageHolder;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;

/**
 * Generates {@link LalExpression} implementation classes from
 * {@link LALScriptModel} AST using Javassist bytecode generation.
 *
 * <p>Because Javassist cannot compile anonymous inner classes,
 * Consumer callbacks are pre-compiled as separate classes and
 * stored as fields on the main class.
 */
@Slf4j
public final class LALClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.";

    private static final String FILTER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec";
    private static final String BINDING =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding";
    private static final String BINDING_PARSED =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding.Parsed";
    private static final String H =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper";
    private static final String EXTRACTOR_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.ExtractorSpec";
    private static final String SLOW_SQL_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.slowsql.SlowSqlSpec";
    private static final String SAMPLED_TRACE_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.sampledtrace.SampledTraceSpec";
    private static final String SINK_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.SinkSpec";
    private static final String SAMPLER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.SamplerSpec";
    private static final String RATE_LIMITING_SAMPLER =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.sampler.RateLimitingSampler";
    private static final String SAMPLE_BUILDER =
        EXTRACTOR_SPEC + "$SampleBuilder";
    private static final String PROCESS_REGISTRY =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry";

    private static final java.util.Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private File classOutputDir;
    private String classNameHint;

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


    /**
     * Compiles a LAL DSL script into a LalExpression implementation.
     */
    public LalExpression compile(final String dsl) throws Exception {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        return compileFromModel(model);
    }

    /**
     * Compiles from a pre-parsed model.
     */
    public LalExpression compileFromModel(final LALScriptModel model) throws Exception {
        final String className = makeClassName("LalExpr_");

        // Phase 1: Collect all consumer info in traversal order
        final List<ConsumerInfo> consumers = new ArrayList<>();
        collectConsumers(model.getStatements(), consumers);

        // Phase 2: Compile consumer classes, named by purpose (extractor/sink/etc.)
        // Defer detach until after main class is compiled so types remain in ClassPool
        final List<CtClass> pendingDetach = new ArrayList<>();
        final List<Object> consumerInstances = new ArrayList<>();
        final List<String> consumerFieldNames = new ArrayList<>();
        final List<String> consumerClassNames = new ArrayList<>();
        final java.util.Map<String, Integer> consumerNameCounts = new java.util.HashMap<>();
        for (int i = 0; i < consumers.size(); i++) {
            final String label = consumers.get(i).label();
            final int count = consumerNameCounts.getOrDefault(label, 0);
            consumerNameCounts.put(label, count + 1);
            final String suffix = count == 0 ? label : label + "_" + (count + 1);
            consumerFieldNames.add("_" + suffix);
            final String consumerName = dedupClassName(className + "$" + suffix);
            consumerClassNames.add(consumerName);
            final Object instance = compileConsumerClass(
                consumerName, consumers.get(i), pendingDetach);
            consumerInstances.add(instance);
        }

        // Phase 3: Build main class with consumer fields (actual types)
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression"));

        for (int i = 0; i < consumers.size(); i++) {
            ctClass.addField(CtField.make(
                "public " + consumerClassNames.get(i) + " "
                    + consumerFieldNames.get(i) + ";",
                ctClass));
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        // Phase 4: Generate execute method referencing consumer fields
        final int[] counter = {0};
        final String executeBody = generateExecuteMethod(
            model, counter, consumerFieldNames);

        if (log.isDebugEnabled()) {
            log.debug("LAL compile AST: {}", model);
            log.debug("LAL compile execute():\n{}", executeBody);
        }

        final javassist.CtMethod execMethod = CtNewMethod.make(executeBody, ctClass);
        ctClass.addMethod(execMethod);
        addLocalVariableTable(execMethod, className, new String[][]{
            {"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"},
            {"binding", "L" + BINDING.replace('.', '/') + ";"}
        });

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        for (final CtClass ct : pendingDetach) {
            ct.detach();
        }
        final LalExpression instance = (LalExpression) clazz
            .getDeclaredConstructor().newInstance();

        // Phase 5: Wire consumer fields
        for (int i = 0; i < consumerInstances.size(); i++) {
            clazz.getField(consumerFieldNames.get(i))
                .set(instance, consumerInstances.get(i));
        }

        return instance;
    }

    // ==================== Consumer info ====================

    private static class ConsumerInfo {
        final String body;
        final String castType;
        final List<ConsumerInfo> subConsumers;
        final List<String> subFieldNames;

        ConsumerInfo(final String body, final String castType) {
            this.body = body;
            this.castType = castType;
            this.subConsumers = new ArrayList<>();
            this.subFieldNames = new ArrayList<>();
        }

        ConsumerInfo(final String body, final String castType,
                     final List<ConsumerInfo> subConsumers,
                     final List<String> subFieldNames) {
            this.body = body;
            this.castType = castType;
            this.subConsumers = new ArrayList<>(subConsumers);
            this.subFieldNames = new ArrayList<>(subFieldNames);
        }

        String nextSubFieldName(final ConsumerInfo sub) {
            final String subLabel = sub.label();
            int count = 0;
            for (final ConsumerInfo existing : subConsumers) {
                if (existing.label().equals(subLabel)) {
                    count++;
                }
            }
            final String fieldName = count == 0
                ? "_" + subLabel : "_" + subLabel + "_" + (count + 1);
            subConsumers.add(sub);
            subFieldNames.add(fieldName);
            return fieldName;
        }

        String label() {
            if (castType.endsWith("ExtractorSpec")) {
                return "extractor";
            } else if (castType.endsWith("SinkSpec")) {
                return "sink";
            } else if (castType.endsWith("SamplerSpec")) {
                return "sampler";
            } else if (castType.endsWith("SlowSqlSpec")) {
                return "slowSql";
            } else if (castType.endsWith("SampledTraceSpec")) {
                return "sampledTrace";
            } else if (castType.endsWith("SampleBuilder")) {
                return "sample";
            } else if (castType.endsWith("RateLimitingSampler")) {
                return "rateLimiter";
            }
            final int dot = castType.lastIndexOf('.');
            final int dollar = castType.lastIndexOf('$');
            final int start = Math.max(dot, dollar) + 1;
            return Character.toLowerCase(castType.charAt(start))
                + castType.substring(start + 1);
        }
    }

    // ==================== Phase 1: Collect consumers ====================

    private void collectConsumers(
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final List<ConsumerInfo> consumers) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            collectConsumerFromStatement(stmt, consumers);
        }
    }

    private void collectConsumerFromStatement(
            final LALScriptModel.FilterStatement stmt,
            final List<ConsumerInfo> consumers) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                final StringBuilder sb = new StringBuilder();
                sb.append("  _t.regexp(\"")
                  .append(escapeJava(tp.getRegexpPattern()))
                  .append("\");\n");
                consumers.add(new ConsumerInfo(sb.toString(),
                    "org.apache.skywalking.oap.log.analyzer.v2.dsl"
                    + ".spec.parser.TextParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            if (((LALScriptModel.JsonParser) stmt).isAbortOnFailure()) {
                consumers.add(new ConsumerInfo(
                    "  _t.abortOnFailure();\n",
                    "org.apache.skywalking.oap.log.analyzer.v2.dsl"
                    + ".spec.parser.JsonParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            if (((LALScriptModel.YamlParser) stmt).isAbortOnFailure()) {
                consumers.add(new ConsumerInfo(
                    "  _t.abortOnFailure();\n",
                    "org.apache.skywalking.oap.log.analyzer.v2.dsl"
                    + ".spec.parser.YamlParserSpec"));
            }
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            final LALScriptModel.ExtractorBlock block =
                (LALScriptModel.ExtractorBlock) stmt;
            final ConsumerInfo info = new ConsumerInfo("", EXTRACTOR_SPEC);
            final StringBuilder sb = new StringBuilder();
            final List<LALScriptModel.FilterStatement> extractorStmts = new ArrayList<>();
            for (final LALScriptModel.ExtractorStatement es : block.getStatements()) {
                extractorStmts.add((LALScriptModel.FilterStatement) es);
            }
            generateExtractorBody(sb, extractorStmts, info);
            consumers.add(new ConsumerInfo(sb.toString(), EXTRACTOR_SPEC,
                info.subConsumers, info.subFieldNames));
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (!sink.getStatements().isEmpty()) {
                final ConsumerInfo info = new ConsumerInfo("", SINK_SPEC);
                final StringBuilder sb = new StringBuilder();
                final List<LALScriptModel.FilterStatement> sinkStmts = new ArrayList<>();
                for (final LALScriptModel.SinkStatement ss : sink.getStatements()) {
                    sinkStmts.add((LALScriptModel.FilterStatement) ss);
                }
                generateSinkBody(sb, sinkStmts, info);
                consumers.add(new ConsumerInfo(sb.toString(), SINK_SPEC,
                    info.subConsumers, info.subFieldNames));
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
            collectConsumers(ifBlock.getThenBranch(), consumers);
            if (!ifBlock.getElseBranch().isEmpty()) {
                collectConsumers(ifBlock.getElseBranch(), consumers);
            }
        }
    }

    // ==================== Extractor body generation ====================

    private void generateExtractorBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.FieldAssignment) {
                final LALScriptModel.FieldAssignment field =
                    (LALScriptModel.FieldAssignment) stmt;
                sb.append("  _t.").append(field.getFieldType().name().toLowerCase())
                  .append("(");
                generateCastedValueAccess(sb, field.getValue(),
                    field.getCastType());
                if (field.getFormatPattern() != null) {
                    sb.append(", \"")
                      .append(escapeJava(field.getFormatPattern()))
                      .append("\"");
                }
                sb.append(");\n");
            } else if (stmt instanceof LALScriptModel.TagAssignment) {
                generateTagAssignment(sb, (LALScriptModel.TagAssignment) stmt);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInBody(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, true);
            } else if (stmt instanceof LALScriptModel.MetricsBlock) {
                generateMetricsSubConsumer(sb, (LALScriptModel.MetricsBlock) stmt,
                    parentInfo);
            } else if (stmt instanceof LALScriptModel.SlowSqlBlock) {
                generateSlowSqlSubConsumer(sb, (LALScriptModel.SlowSqlBlock) stmt,
                    parentInfo);
            } else if (stmt instanceof LALScriptModel.SampledTraceBlock) {
                generateSampledTraceSubConsumer(sb,
                    (LALScriptModel.SampledTraceBlock) stmt,
                    parentInfo);
            }
        }
    }

    private void generateIfBlockInBody(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final ConsumerInfo parentInfo,
            final boolean isExtractorContext) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        if (isExtractorContext) {
            generateExtractorBody(sb, ifBlock.getThenBranch(), parentInfo);
        } else {
            generateSinkBody(sb, ifBlock.getThenBranch(), parentInfo);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            if (isExtractorContext) {
                generateExtractorBody(sb, ifBlock.getElseBranch(), parentInfo);
            } else {
                generateSinkBody(sb, ifBlock.getElseBranch(), parentInfo);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Metrics sub-consumer ====================

    private void generateMetricsSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.MetricsBlock block,
            final ConsumerInfo parentInfo) {
        final StringBuilder body = new StringBuilder();
        if (block.getName() != null) {
            body.append("  _t.name(\"")
                .append(escapeJava(block.getName())).append("\");\n");
        }
        if (block.getTimestampValue() != null) {
            body.append("  _t.timestamp(");
            generateCastedValueAccess(body, block.getTimestampValue(),
                block.getTimestampCast());
            body.append(");\n");
        }
        if (!block.getLabels().isEmpty()) {
            body.append("  { java.util.Map _labels = new java.util.LinkedHashMap();\n");
            for (final Map.Entry<String, LALScriptModel.TagValue> entry
                    : block.getLabels().entrySet()) {
                body.append("    _labels.put(\"")
                    .append(escapeJava(entry.getKey())).append("\", ");
                generateCastedValueAccess(body, entry.getValue().getValue(),
                    entry.getValue().getCastType());
                body.append(");\n");
            }
            body.append("    _t.labels(_labels); }\n");
        }
        if (block.getValue() != null) {
            body.append("  _t.value(");
            if ("Long".equals(block.getValueCast())) {
                body.append("(double) ").append(H).append(".toLong(");
                generateValueAccess(body, block.getValue());
                body.append(")");
            } else if ("Integer".equals(block.getValueCast())) {
                body.append("(double) ").append(H).append(".toInt(");
                generateValueAccess(body, block.getValue());
                body.append(")");
            } else {
                // Number literal or untyped value — cast to double for Sample.value(double)
                if (block.getValue().isNumberLiteral()) {
                    body.append("(double) ").append(block.getValue().getSegments().get(0));
                } else {
                    body.append("((Number) ");
                    generateValueAccess(body, block.getValue());
                    body.append(").doubleValue()");
                }
            }
            body.append(");\n");
        }

        final ConsumerInfo sub = new ConsumerInfo(body.toString(), SAMPLE_BUILDER);
        final String fieldName = parentInfo.nextSubFieldName(sub);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(this.binding);\n");
        sb.append("  _t.metrics(this.").append(fieldName).append(");\n");
    }

    // ==================== SlowSql sub-consumer ====================

    private void generateSlowSqlSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SlowSqlBlock block,
            final ConsumerInfo parentInfo) {
        final StringBuilder body = new StringBuilder();
        if (block.getId() != null) {
            body.append("  _t.id(");
            generateCastedValueAccess(body, block.getId(), block.getIdCast());
            body.append(");\n");
        }
        if (block.getStatement() != null) {
            body.append("  _t.statement(");
            generateCastedValueAccess(body, block.getStatement(),
                block.getStatementCast());
            body.append(");\n");
        }
        if (block.getLatency() != null) {
            body.append("  _t.latency(Long.valueOf(").append(H).append(".toLong(");
            generateValueAccess(body, block.getLatency());
            body.append(")));\n");
        }

        final ConsumerInfo sub = new ConsumerInfo(body.toString(), SLOW_SQL_SPEC);
        final String fieldName = parentInfo.nextSubFieldName(sub);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(this.binding);\n");
        sb.append("  _t.slowSql(this.").append(fieldName).append(");\n");
    }

    // ==================== SampledTrace sub-consumer ====================

    private void generateSampledTraceSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceBlock block,
            final ConsumerInfo parentInfo) {
        final StringBuilder body = new StringBuilder();
        final ConsumerInfo sub = new ConsumerInfo("", SAMPLED_TRACE_SPEC);
        generateSampledTraceBody(body, block.getStatements(), sub);

        // Propagate any sub-sub-consumers
        final ConsumerInfo propagated = new ConsumerInfo(body.toString(),
            SAMPLED_TRACE_SPEC, sub.subConsumers, sub.subFieldNames);
        final String fieldName = parentInfo.nextSubFieldName(propagated);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(this.binding);\n");
        sb.append("  _t.sampledTrace(this.").append(fieldName).append(");\n");
    }

    private void generateSampledTraceBody(
            final StringBuilder sb,
            final List<LALScriptModel.SampledTraceStatement> stmts,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.SampledTraceStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb, (LALScriptModel.SampledTraceField) stmt);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo);
            }
        }
    }

    private void generateSampledTraceField(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceField field) {
        final String methodName;
        switch (field.getFieldType()) {
            case LATENCY:
                methodName = "latency";
                sb.append("  _t.latency(Long.valueOf(").append(H).append(".toLong(");
                generateValueAccess(sb, field.getValue());
                sb.append(")));\n");
                return;
            case COMPONENT_ID:
                methodName = "componentId";
                sb.append("  _t.componentId(").append(H).append(".toInt(");
                generateValueAccess(sb, field.getValue());
                sb.append("));\n");
                return;
            case URI:
                methodName = "uri";
                break;
            case REASON:
                methodName = "reason";
                break;
            case PROCESS_ID:
                methodName = "processId";
                break;
            case DEST_PROCESS_ID:
                methodName = "destProcessId";
                break;
            case DETECT_POINT:
                methodName = "detectPoint";
                break;
            case REPORT_SERVICE:
                methodName = "reportService";
                break;
            default:
                return;
        }
        sb.append("  _t.").append(methodName).append("(");
        generateCastedValueAccess(sb, field.getValue(), field.getCastType());
        sb.append(");\n");
    }

    private void generateSampledTraceIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final ConsumerInfo parentInfo) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getThenBranch(),
            parentInfo);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getElseBranch(),
                parentInfo);
            sb.append("  }\n");
        }
    }

    private void generateSampledTraceBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb,
                    (LALScriptModel.SampledTraceField) stmt);
            } else if (stmt instanceof LALScriptModel.FieldAssignment) {
                generateSampledTraceFieldFromAssignment(sb,
                    (LALScriptModel.FieldAssignment) stmt);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo);
            }
        }
    }

    private void generateSampledTraceFieldFromAssignment(
            final StringBuilder sb,
            final LALScriptModel.FieldAssignment fa) {
        // Map FieldType to SampledTraceSpec methods
        switch (fa.getFieldType()) {
            case TIMESTAMP:
                sb.append("  _t.latency(Long.valueOf(").append(H).append(".toLong(");
                generateValueAccess(sb, fa.getValue());
                sb.append(")));\n");
                break;
            default:
                sb.append("  _t.").append(fa.getFieldType().name().toLowerCase())
                  .append("(");
                generateCastedValueAccess(sb, fa.getValue(), fa.getCastType());
                sb.append(");\n");
                break;
        }
    }

    // ==================== Sink body generation ====================

    private void generateSinkBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.EnforcerStatement) {
                sb.append("  _t.enforcer();\n");
            } else if (stmt instanceof LALScriptModel.DropperStatement) {
                sb.append("  _t.dropper();\n");
            } else if (stmt instanceof LALScriptModel.SamplerBlock) {
                generateSamplerSubConsumer(sb, (LALScriptModel.SamplerBlock) stmt,
                    parentInfo);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInBody(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, false);
            }
        }
    }

    // ==================== Sampler sub-consumer ====================

    private void generateSamplerSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SamplerBlock block,
            final ConsumerInfo parentInfo) {
        final StringBuilder body = new StringBuilder();
        final ConsumerInfo sub = new ConsumerInfo("", SAMPLER_SPEC);
        generateSamplerBody(body, block.getContents(), sub);

        final ConsumerInfo propagated = new ConsumerInfo(body.toString(),
            SAMPLER_SPEC, sub.subConsumers, sub.subFieldNames);
        final String fieldName = parentInfo.nextSubFieldName(propagated);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(this.binding);\n");
        sb.append("  _t.sampler(this.").append(fieldName).append(");\n");
    }

    private void generateSamplerBody(
            final StringBuilder sb,
            final List<LALScriptModel.SamplerContent> contents,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.SamplerContent content : contents) {
            if (content instanceof LALScriptModel.RateLimitBlock) {
                generateRateLimitSubConsumer(sb, (LALScriptModel.RateLimitBlock) content,
                    parentInfo);
            } else if (content instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) content,
                    parentInfo);
            }
        }
    }

    private void generateSamplerIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final ConsumerInfo parentInfo) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        generateSamplerBodyFromFilterStmts(sb, ifBlock.getThenBranch(),
            parentInfo);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSamplerBodyFromFilterStmts(sb, ifBlock.getElseBranch(),
                parentInfo);
            sb.append("  }\n");
        }
    }

    private void generateSamplerBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SamplerBlock) {
                // SamplerBlock appears in if-branches inside a sampler,
                // generate its contents inline
                generateSamplerBody(sb,
                    ((LALScriptModel.SamplerBlock) stmt).getContents(),
                    parentInfo);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo);
            }
        }
    }

    private void generateRateLimitSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.RateLimitBlock block,
            final ConsumerInfo parentInfo) {
        final String body = "  _t.rpm(" + block.getRpm() + ");\n";
        final ConsumerInfo sub = new ConsumerInfo(body, RATE_LIMITING_SAMPLER);
        final String fieldName = parentInfo.nextSubFieldName(sub);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(this.binding);\n");

        if (block.isIdInterpolated()) {
            sb.append("  _t.rateLimit(\"\"");
            for (final LALScriptModel.InterpolationPart part : block.getIdParts()) {
                sb.append(" + ");
                if (part.isLiteral()) {
                    sb.append("\"").append(escapeJava(part.getLiteral())).append("\"");
                } else {
                    sb.append("String.valueOf(");
                    generateValueAccess(sb, part.getExpression());
                    sb.append(")");
                }
            }
            sb.append(", this.").append(fieldName).append(");\n");
        } else {
            sb.append("  _t.rateLimit(\"")
              .append(escapeJava(block.getId())).append("\", this.")
              .append(fieldName).append(");\n");
        }
    }

    private void generateTagAssignment(final StringBuilder sb,
                                         final LALScriptModel.TagAssignment tag) {
        final Map<String, LALScriptModel.TagValue> tags = tag.getTags();
        if (tags.isEmpty()) {
            return;
        }
        if (tags.size() == 1) {
            final Map.Entry<String, LALScriptModel.TagValue> entry =
                tags.entrySet().iterator().next();
            sb.append("  _t.tag(java.util.Collections.singletonMap(\"")
              .append(escapeJava(entry.getKey())).append("\", ");
            generateCastedValueAccess(sb, entry.getValue().getValue(),
                entry.getValue().getCastType());
            sb.append("));\n");
        } else {
            sb.append("  { java.util.Map _tagMap = new java.util.LinkedHashMap();\n");
            for (final Map.Entry<String, LALScriptModel.TagValue> entry
                    : tags.entrySet()) {
                sb.append("    _tagMap.put(\"")
                  .append(escapeJava(entry.getKey())).append("\", ");
                generateCastedValueAccess(sb, entry.getValue().getValue(),
                    entry.getValue().getCastType());
                sb.append(");\n");
            }
            sb.append("    _t.tag(_tagMap); }\n");
        }
    }

    // ==================== Phase 2: Compile consumer classes ====================

    private Object compileConsumerClass(final String className,
                                         final ConsumerInfo info,
                                         final List<CtClass> pendingDetach) throws Exception {
        // Pre-compile sub-consumers so their types are available in ClassPool
        final List<Object> subInstances = new ArrayList<>();
        final List<String> subClassNames = new ArrayList<>();
        final java.util.Map<String, Integer> subNameCounts = new java.util.HashMap<>();
        for (int i = 0; i < info.subConsumers.size(); i++) {
            final String subLabel = info.subConsumers.get(i).label();
            final int cnt = subNameCounts.getOrDefault(subLabel, 0);
            subNameCounts.put(subLabel, cnt + 1);
            final String subSuffix = cnt == 0 ? subLabel : subLabel + "_" + (cnt + 1);
            final String subName = dedupClassName(className + "$" + subSuffix);
            subClassNames.add(subName);
            subInstances.add(compileConsumerClass(
                subName, info.subConsumers.get(i), pendingDetach));
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get("java.util.function.Consumer"));
        ctClass.addInterface(classPool.get(
            PACKAGE_PREFIX + "BindingAware"));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        ctClass.addField(CtField.make(
            "private " + BINDING + " binding;", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "public void setBinding(" + BINDING + " b) {"
            + " this.binding = b; }", ctClass));
        ctClass.addMethod(CtNewMethod.make(
            "public " + BINDING + " getBinding() {"
            + " return this.binding; }", ctClass));

        // Add sub-consumer fields with actual types
        for (int i = 0; i < info.subConsumers.size(); i++) {
            ctClass.addField(CtField.make(
                "public " + subClassNames.get(i) + " "
                    + info.subFieldNames.get(i) + ";",
                ctClass));
        }

        final String method = "public void accept(Object arg) {\n"
            + "  " + info.castType + " _t = (" + info.castType + ") arg;\n"
            + info.body
            + "}\n";

        if (log.isDebugEnabled()) {
            log.debug("LAL compile consumer {} body:\n{}", className, method);
        }

        final javassist.CtMethod acceptMethod = CtNewMethod.make(method, ctClass);
        ctClass.addMethod(acceptMethod);
        addLocalVariableTable(acceptMethod, className, new String[][]{
            {"arg", "Ljava/lang/Object;"},
            {"_t", "L" + info.castType.replace('.', '/') + ";"}
        });

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        pendingDetach.add(ctClass);
        final Object instance = clazz.getDeclaredConstructor().newInstance();

        // Wire pre-compiled sub-consumer fields
        for (int i = 0; i < subInstances.size(); i++) {
            clazz.getField(info.subFieldNames.get(i)).set(instance, subInstances.get(i));
        }

        return instance;
    }

    // ==================== Phase 4: Generate execute method ====================

    private String generateExecuteMethod(final LALScriptModel model,
                                          final int[] counter,
                                          final List<String> fieldNames) {
        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(BINDING).append(" binding) {\n");

        for (final LALScriptModel.FilterStatement stmt
                : model.getStatements()) {
            generateFilterStatement(sb, stmt, counter, fieldNames);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateFilterStatement(final StringBuilder sb,
                                          final LALScriptModel.FilterStatement stmt,
                                          final int[] counter,
                                          final List<String> fieldNames) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                emitConsumerCall(sb, "filterSpec.text", counter, fieldNames);
            } else {
                sb.append("  filterSpec.text();\n");
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            if (((LALScriptModel.JsonParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.json", counter, fieldNames);
            } else {
                sb.append("  filterSpec.json();\n");
            }
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            if (((LALScriptModel.YamlParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.yaml", counter, fieldNames);
            } else {
                sb.append("  filterSpec.yaml();\n");
            }
        } else if (stmt instanceof LALScriptModel.AbortStatement) {
            sb.append("  filterSpec.abort();\n");
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            emitConsumerCall(sb, "filterSpec.extractor", counter, fieldNames);
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink();\n");
            } else {
                emitConsumerCall(sb, "filterSpec.sink", counter, fieldNames);
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            generateIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                counter, fieldNames);
        }
    }

    private void emitConsumerCall(final StringBuilder sb,
                                   final String methodPrefix,
                                   final int[] counter,
                                   final List<String> fieldNames) {
        final String fieldName = fieldNames.get(counter[0]++);
        sb.append("  this.").append(fieldName)
          .append(".setBinding(binding);\n");
        sb.append("  ").append(methodPrefix)
          .append("(this.").append(fieldName).append(");\n");
    }

    private void generateIfBlock(final StringBuilder sb,
                                  final LALScriptModel.IfBlock ifBlock,
                                  final int[] counter,
                                  final List<String> fieldNames) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        for (final LALScriptModel.FilterStatement s : ifBlock.getThenBranch()) {
            generateFilterStatement(sb, s, counter, fieldNames);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            for (final LALScriptModel.FilterStatement s
                    : ifBlock.getElseBranch()) {
                generateFilterStatement(sb, s, counter, fieldNames);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Conditions ====================

    private void generateCondition(final StringBuilder sb,
                                    final LALScriptModel.Condition cond) {
        if (cond instanceof LALScriptModel.ComparisonCondition) {
            final LALScriptModel.ComparisonCondition cc =
                (LALScriptModel.ComparisonCondition) cond;
            switch (cc.getOp()) {
                case EQ:
                    sb.append("java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast());
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight());
                    sb.append(")");
                    break;
                case NEQ:
                    sb.append("!java.util.Objects.equals(");
                    generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast());
                    sb.append(", ");
                    generateConditionValue(sb, cc.getRight());
                    sb.append(")");
                    break;
                case GT:
                    sb.append(H).append(".toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") > ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LT:
                    sb.append(H).append(".toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") < ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case GTE:
                    sb.append(H).append(".toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") >= ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LTE:
                    sb.append(H).append(".toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") <= ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                default:
                    break;
            }
        } else if (cond instanceof LALScriptModel.LogicalCondition) {
            final LALScriptModel.LogicalCondition lc =
                (LALScriptModel.LogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft());
            sb.append(lc.getOp() == LALScriptModel.LogicalOp.AND
                ? " && " : " || ");
            generateCondition(sb, lc.getRight());
            sb.append(")");
        } else if (cond instanceof LALScriptModel.NotCondition) {
            sb.append("!(");
            generateCondition(sb,
                ((LALScriptModel.NotCondition) cond).getInner());
            sb.append(")");
        } else if (cond instanceof LALScriptModel.ExprCondition) {
            sb.append(H).append(".isTruthy(");
            generateValueAccessObj(sb,
                ((LALScriptModel.ExprCondition) cond).getExpr(),
                ((LALScriptModel.ExprCondition) cond).getCastType());
            sb.append(")");
        }
    }

    private void generateConditionValue(final StringBuilder sb,
                                         final LALScriptModel.ConditionValue cv) {
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
                null);
        }
    }

    private void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            sb.append((long) ((LALScriptModel.NumberConditionValue) cv)
                .getValue()).append("L");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            sb.append(H).append(".toLong(");
            generateValueAccessObj(sb,
                ((LALScriptModel.ValueAccessConditionValue) cv).getValue(),
                null);
            sb.append(")");
        } else {
            sb.append("0L");
        }
    }

    // ==================== Value access ====================

    private void generateCastedValueAccess(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final String castType) {
        if ("String".equals(castType)) {
            sb.append(H).append(".toStr(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append(H).append(".toLong(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Integer".equals(castType)) {
            sb.append(H).append(".toInt(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Boolean".equals(castType)) {
            sb.append(H).append(".toBool(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else {
            generateValueAccess(sb, value);
        }
    }

    private void generateValueAccessObj(final StringBuilder sb,
                                         final LALScriptModel.ValueAccess value,
                                         final String castType) {
        if ("String".equals(castType)) {
            sb.append(H).append(".toStr(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else {
            generateValueAccess(sb, value);
        }
    }

    private void generateValueAccess(final StringBuilder sb,
                                      final LALScriptModel.ValueAccess value) {
        // Handle function call primaries (e.g., tag("LOG_KIND"))
        if (value.getFunctionCallName() != null) {
            if ("tag".equals(value.getFunctionCallName())
                    && !value.getFunctionCallArgs().isEmpty()) {
                // tag("KEY") → tagValue(binding, "KEY")
                sb.append(H).append(".tagValue(binding, \"");
                final String key = value.getFunctionCallArgs().get(0)
                    .getValue().getSegments().get(0);
                sb.append(escapeJava(key)).append("\")");
            } else {
                // Unknown function call — emit null for safety
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
            // Box number literals so Javassist resolves Object-param methods
            if (num.contains(".")) {
                sb.append("Double.valueOf(").append(num).append(")");
            } else {
                sb.append("Integer.valueOf(").append(num).append(")");
            }
            return;
        }

        // Handle ProcessRegistry static calls
        if (value.isProcessRegistryRef()) {
            generateProcessRegistryCall(sb, value);
            return;
        }

        String current;
        if (value.isParsedRef()) {
            current = "binding.parsed()";
        } else if (value.isLogRef()) {
            current = "binding.log()";
        } else {
            final List<LALScriptModel.ValueAccessSegment> segs = value.getChain();
            if (segs.isEmpty()) {
                sb.append("null");
                return;
            }
            current = "binding.parsed()";
        }

        final List<LALScriptModel.ValueAccessSegment> chain = value.getChain();
        if (chain.isEmpty()) {
            sb.append(current);
            return;
        }

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                final String name =
                    ((LALScriptModel.FieldSegment) seg).getName();
                // getAt() already handles null → null, so safe nav is free
                current = H + ".getAt(" + current + ", \""
                    + escapeJava(name) + "\")";
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms =
                    (LALScriptModel.MethodSegment) seg;
                if (ms.isSafeNav()) {
                    // Safe navigation: null-safe method call
                    current = H + ".safeCall(" + current + ", \""
                        + escapeJava(ms.getName()) + "\")";
                } else {
                    if (ms.getArguments().isEmpty()) {
                        current = current + "." + ms.getName() + "()";
                    } else {
                        current = current + "." + ms.getName() + "("
                            + generateMethodArgs(ms.getArguments()) + ")";
                    }
                }
            }
        }
        sb.append(current);
    }

    private void generateProcessRegistryCall(
            final StringBuilder sb,
            final LALScriptModel.ValueAccess value) {
        final List<LALScriptModel.ValueAccessSegment> chain = value.getChain();
        if (chain.isEmpty()) {
            sb.append("null");
            return;
        }
        // Expect exactly one method segment: ProcessRegistry.methodName(args)
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
                    args.get(i).getValue(), args.get(i).getCastType());
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
            generateCastedValueAccess(sb,
                args.get(i).getValue(), args.get(i).getCastType());
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
     * Generates the Java source body of the execute method for
     * debugging/testing.
     */
    public String generateSource(final String dsl) {
        final LALScriptModel model = LALScriptParser.parse(dsl);
        final List<ConsumerInfo> consumers = new ArrayList<>();
        collectConsumers(model.getStatements(), consumers);
        // Build field names for source generation
        final List<String> fieldNames = new ArrayList<>();
        final java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
        for (final ConsumerInfo ci : consumers) {
            final String label = ci.label();
            final int count = nameCounts.getOrDefault(label, 0);
            nameCounts.put(label, count + 1);
            final String suffix = count == 0 ? label : label + "_" + (count + 1);
            fieldNames.add("_" + suffix);
        }
        final int[] counter = {0};
        return generateExecuteMethod(model, counter, fieldNames);
    }
}
