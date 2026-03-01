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

    private final ClassPool classPool;

    public LALClassGenerator() {
        this(ClassPool.getDefault());
    }

    public LALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
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
        final String className = PACKAGE_PREFIX + "LalExpr_"
            + CLASS_COUNTER.getAndIncrement();

        // Phase 1: Collect all consumer info in traversal order
        final List<ConsumerInfo> consumers = new ArrayList<>();
        collectConsumers(model.getStatements(), consumers);

        // Phase 2: Compile consumer classes (recursively handles sub-consumers)
        final List<Object> consumerInstances = new ArrayList<>();
        for (int i = 0; i < consumers.size(); i++) {
            final String consumerName = className + "_C" + i;
            final Object instance = compileConsumerClass(
                consumerName, consumers.get(i));
            consumerInstances.add(instance);
        }

        // Phase 3: Build main class with consumer fields
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression"));

        for (int i = 0; i < consumers.size(); i++) {
            ctClass.addField(CtField.make(
                "public java.util.function.Consumer _consumer" + i + ";",
                ctClass));
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));
        addHelperMethods(ctClass);

        // Phase 4: Generate execute method referencing consumer fields
        final int[] counter = {0};
        final String executeBody = generateExecuteMethod(model, counter);

        if (log.isDebugEnabled()) {
            log.debug("LAL compile AST: {}", model);
            log.debug("LAL compile execute():\n{}", executeBody);
        }

        ctClass.addMethod(CtNewMethod.make(executeBody, ctClass));

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        final LalExpression instance = (LalExpression) clazz
            .getDeclaredConstructor().newInstance();

        // Phase 5: Wire consumer fields
        for (int i = 0; i < consumerInstances.size(); i++) {
            clazz.getField("_consumer" + i).set(instance, consumerInstances.get(i));
        }

        return instance;
    }

    // ==================== Consumer info ====================

    private static class ConsumerInfo {
        final String body;
        final String castType;
        final List<ConsumerInfo> subConsumers;

        ConsumerInfo(final String body, final String castType) {
            this.body = body;
            this.castType = castType;
            this.subConsumers = new ArrayList<>();
        }

        ConsumerInfo(final String body, final String castType,
                     final List<ConsumerInfo> subConsumers) {
            this.body = body;
            this.castType = castType;
            this.subConsumers = new ArrayList<>(subConsumers);
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
            final int[] subCounter = {0};
            final List<LALScriptModel.FilterStatement> extractorStmts = new ArrayList<>();
            for (final LALScriptModel.ExtractorStatement es : block.getStatements()) {
                extractorStmts.add((LALScriptModel.FilterStatement) es);
            }
            generateExtractorBody(sb, extractorStmts, info, subCounter);
            consumers.add(new ConsumerInfo(sb.toString(), EXTRACTOR_SPEC,
                info.subConsumers));
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (!sink.getStatements().isEmpty()) {
                final ConsumerInfo info = new ConsumerInfo("", SINK_SPEC);
                final StringBuilder sb = new StringBuilder();
                final int[] subCounter = {0};
                final List<LALScriptModel.FilterStatement> sinkStmts = new ArrayList<>();
                for (final LALScriptModel.SinkStatement ss : sink.getStatements()) {
                    sinkStmts.add((LALScriptModel.FilterStatement) ss);
                }
                generateSinkBody(sb, sinkStmts, info, subCounter);
                consumers.add(new ConsumerInfo(sb.toString(), SINK_SPEC,
                    info.subConsumers));
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
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
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
                    parentInfo, subCounter, true);
            } else if (stmt instanceof LALScriptModel.MetricsBlock) {
                generateMetricsSubConsumer(sb, (LALScriptModel.MetricsBlock) stmt,
                    parentInfo, subCounter);
            } else if (stmt instanceof LALScriptModel.SlowSqlBlock) {
                generateSlowSqlSubConsumer(sb, (LALScriptModel.SlowSqlBlock) stmt,
                    parentInfo, subCounter);
            } else if (stmt instanceof LALScriptModel.SampledTraceBlock) {
                generateSampledTraceSubConsumer(sb,
                    (LALScriptModel.SampledTraceBlock) stmt,
                    parentInfo, subCounter);
            }
        }
    }

    private void generateIfBlockInBody(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final ConsumerInfo parentInfo,
            final int[] subCounter,
            final boolean isExtractorContext) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        if (isExtractorContext) {
            generateExtractorBody(sb, ifBlock.getThenBranch(), parentInfo, subCounter);
        } else {
            generateSinkBody(sb, ifBlock.getThenBranch(), parentInfo, subCounter);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            if (isExtractorContext) {
                generateExtractorBody(sb, ifBlock.getElseBranch(), parentInfo, subCounter);
            } else {
                generateSinkBody(sb, ifBlock.getElseBranch(), parentInfo, subCounter);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Metrics sub-consumer ====================

    private void generateMetricsSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.MetricsBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        final int idx = subCounter[0]++;
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
                body.append("(double) toLong(");
                generateValueAccess(body, block.getValue());
                body.append(")");
            } else if ("Integer".equals(block.getValueCast())) {
                body.append("(double) toInt(");
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
        parentInfo.subConsumers.add(sub);
        sb.append("  ((").append(PACKAGE_PREFIX)
          .append("BindingAware) this._sub").append(idx)
          .append(").setBinding(this.binding);\n");
        sb.append("  _t.metrics(this._sub").append(idx).append(");\n");
    }

    // ==================== SlowSql sub-consumer ====================

    private void generateSlowSqlSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SlowSqlBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        final int idx = subCounter[0]++;
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
            body.append("  _t.latency(Long.valueOf(toLong(");
            generateValueAccess(body, block.getLatency());
            body.append(")));\n");
        }

        final ConsumerInfo sub = new ConsumerInfo(body.toString(), SLOW_SQL_SPEC);
        parentInfo.subConsumers.add(sub);
        sb.append("  ((").append(PACKAGE_PREFIX)
          .append("BindingAware) this._sub").append(idx)
          .append(").setBinding(this.binding);\n");
        sb.append("  _t.slowSql(this._sub").append(idx).append(");\n");
    }

    // ==================== SampledTrace sub-consumer ====================

    private void generateSampledTraceSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        final int idx = subCounter[0]++;
        final StringBuilder body = new StringBuilder();
        final ConsumerInfo sub = new ConsumerInfo("", SAMPLED_TRACE_SPEC);
        final int[] innerSubCounter = {0};
        generateSampledTraceBody(body, block.getStatements(), sub, innerSubCounter);

        // Propagate any sub-sub-consumers
        parentInfo.subConsumers.add(new ConsumerInfo(body.toString(),
            SAMPLED_TRACE_SPEC, sub.subConsumers));
        sb.append("  ((").append(PACKAGE_PREFIX)
          .append("BindingAware) this._sub").append(idx)
          .append(").setBinding(this.binding);\n");
        sb.append("  _t.sampledTrace(this._sub").append(idx).append(");\n");
    }

    private void generateSampledTraceBody(
            final StringBuilder sb,
            final List<LALScriptModel.SampledTraceStatement> stmts,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        for (final LALScriptModel.SampledTraceStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb, (LALScriptModel.SampledTraceField) stmt);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, subCounter);
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
                sb.append("  _t.latency(Long.valueOf(toLong(");
                generateValueAccess(sb, field.getValue());
                sb.append(")));\n");
                return;
            case COMPONENT_ID:
                methodName = "componentId";
                sb.append("  _t.componentId(toInt(");
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
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getThenBranch(),
            parentInfo, subCounter);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSampledTraceBodyFromFilterStmts(sb, ifBlock.getElseBranch(),
                parentInfo, subCounter);
            sb.append("  }\n");
        }
    }

    private void generateSampledTraceBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb,
                    (LALScriptModel.SampledTraceField) stmt);
            } else if (stmt instanceof LALScriptModel.FieldAssignment) {
                generateSampledTraceFieldFromAssignment(sb,
                    (LALScriptModel.FieldAssignment) stmt);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, subCounter);
            }
        }
    }

    private void generateSampledTraceFieldFromAssignment(
            final StringBuilder sb,
            final LALScriptModel.FieldAssignment fa) {
        // Map FieldType to SampledTraceSpec methods
        switch (fa.getFieldType()) {
            case TIMESTAMP:
                sb.append("  _t.latency(Long.valueOf(toLong(");
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
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.EnforcerStatement) {
                sb.append("  _t.enforcer();\n");
            } else if (stmt instanceof LALScriptModel.DropperStatement) {
                sb.append("  _t.dropper();\n");
            } else if (stmt instanceof LALScriptModel.SamplerBlock) {
                generateSamplerSubConsumer(sb, (LALScriptModel.SamplerBlock) stmt,
                    parentInfo, subCounter);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInBody(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, subCounter, false);
            }
        }
    }

    // ==================== Sampler sub-consumer ====================

    private void generateSamplerSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.SamplerBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        final int idx = subCounter[0]++;
        final StringBuilder body = new StringBuilder();
        final ConsumerInfo sub = new ConsumerInfo("", SAMPLER_SPEC);
        final int[] innerSubCounter = {0};
        generateSamplerBody(body, block.getContents(), sub, innerSubCounter);

        parentInfo.subConsumers.add(new ConsumerInfo(body.toString(),
            SAMPLER_SPEC, sub.subConsumers));
        sb.append("  ((").append(PACKAGE_PREFIX)
          .append("BindingAware) this._sub").append(idx)
          .append(").setBinding(this.binding);\n");
        sb.append("  _t.sampler(this._sub").append(idx).append(");\n");
    }

    private void generateSamplerBody(
            final StringBuilder sb,
            final List<LALScriptModel.SamplerContent> contents,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        for (final LALScriptModel.SamplerContent content : contents) {
            if (content instanceof LALScriptModel.RateLimitBlock) {
                generateRateLimitSubConsumer(sb, (LALScriptModel.RateLimitBlock) content,
                    parentInfo, subCounter);
            } else if (content instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) content,
                    parentInfo, subCounter);
            }
        }
    }

    private void generateSamplerIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        generateSamplerBodyFromFilterStmts(sb, ifBlock.getThenBranch(),
            parentInfo, subCounter);
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            generateSamplerBodyFromFilterStmts(sb, ifBlock.getElseBranch(),
                parentInfo, subCounter);
            sb.append("  }\n");
        }
    }

    private void generateSamplerBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SamplerBlock) {
                // SamplerBlock appears in if-branches inside a sampler
                generateSamplerSubConsumerInline(sb,
                    (LALScriptModel.SamplerBlock) stmt,
                    parentInfo, subCounter);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) stmt,
                    parentInfo, subCounter);
            }
        }
    }

    private void generateSamplerSubConsumerInline(
            final StringBuilder sb,
            final LALScriptModel.SamplerBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        // When a sampler block appears inside an if branch of a sampler,
        // generate its contents inline
        generateSamplerBody(sb, block.getContents(), parentInfo, subCounter);
    }

    private void generateRateLimitSubConsumer(
            final StringBuilder sb,
            final LALScriptModel.RateLimitBlock block,
            final ConsumerInfo parentInfo,
            final int[] subCounter) {
        final int idx = subCounter[0]++;
        final String body = "  _t.rpm(" + block.getRpm() + ");\n";
        final ConsumerInfo sub = new ConsumerInfo(body, RATE_LIMITING_SAMPLER);
        parentInfo.subConsumers.add(sub);
        sb.append("  ((").append(PACKAGE_PREFIX)
          .append("BindingAware) this._sub").append(idx)
          .append(").setBinding(this.binding);\n");

        if (block.isIdInterpolated()) {
            // Emit string concatenation for interpolated IDs
            // e.g. "${log.service}:${parsed?.field}" →
            //   "" + String.valueOf(binding.log().getService()) + ":" + String.valueOf(...)
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
            sb.append(", this._sub").append(idx).append(");\n");
        } else {
            sb.append("  _t.rateLimit(\"")
              .append(escapeJava(block.getId())).append("\", this._sub")
              .append(idx).append(");\n");
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
                                         final ConsumerInfo info) throws Exception {
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

        // Add sub-consumer fields
        for (int i = 0; i < info.subConsumers.size(); i++) {
            ctClass.addField(CtField.make(
                "public java.util.function.Consumer _sub" + i + ";",
                ctClass));
        }

        addHelperMethods(ctClass);

        final String method = "public void accept(Object arg) {\n"
            + "  " + info.castType + " _t = (" + info.castType + ") arg;\n"
            + info.body
            + "}\n";

        if (log.isDebugEnabled()) {
            log.debug("LAL compile consumer {} body:\n{}", className, method);
        }

        ctClass.addMethod(CtNewMethod.make(method, ctClass));

        final Class<?> clazz = ctClass.toClass(LalExpressionPackageHolder.class);
        ctClass.detach();
        final Object instance = clazz.getDeclaredConstructor().newInstance();

        // Compile and wire sub-consumers
        for (int i = 0; i < info.subConsumers.size(); i++) {
            final String subName = className + "_S" + i;
            final Object subInstance = compileConsumerClass(
                subName, info.subConsumers.get(i));
            clazz.getField("_sub" + i).set(instance, subInstance);
        }

        return instance;
    }

    // ==================== Phase 4: Generate execute method ====================

    private String generateExecuteMethod(final LALScriptModel model,
                                          final int[] counter) {
        final StringBuilder sb = new StringBuilder();
        sb.append("public void execute(").append(FILTER_SPEC)
          .append(" filterSpec, ").append(BINDING).append(" binding) {\n");

        for (final LALScriptModel.FilterStatement stmt
                : model.getStatements()) {
            generateFilterStatement(sb, stmt, counter);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateFilterStatement(final StringBuilder sb,
                                          final LALScriptModel.FilterStatement stmt,
                                          final int[] counter) {
        if (stmt instanceof LALScriptModel.TextParser) {
            final LALScriptModel.TextParser tp = (LALScriptModel.TextParser) stmt;
            if (tp.getRegexpPattern() != null) {
                emitConsumerCall(sb, "filterSpec.text", counter);
            } else {
                sb.append("  filterSpec.text();\n");
            }
        } else if (stmt instanceof LALScriptModel.JsonParser) {
            if (((LALScriptModel.JsonParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.json", counter);
            } else {
                sb.append("  filterSpec.json();\n");
            }
        } else if (stmt instanceof LALScriptModel.YamlParser) {
            if (((LALScriptModel.YamlParser) stmt).isAbortOnFailure()) {
                emitConsumerCall(sb, "filterSpec.yaml", counter);
            } else {
                sb.append("  filterSpec.yaml();\n");
            }
        } else if (stmt instanceof LALScriptModel.AbortStatement) {
            sb.append("  filterSpec.abort();\n");
        } else if (stmt instanceof LALScriptModel.ExtractorBlock) {
            emitConsumerCall(sb, "filterSpec.extractor", counter);
        } else if (stmt instanceof LALScriptModel.SinkBlock) {
            final LALScriptModel.SinkBlock sink = (LALScriptModel.SinkBlock) stmt;
            if (sink.getStatements().isEmpty()) {
                sb.append("  filterSpec.sink();\n");
            } else {
                emitConsumerCall(sb, "filterSpec.sink", counter);
            }
        } else if (stmt instanceof LALScriptModel.IfBlock) {
            generateIfBlock(sb, (LALScriptModel.IfBlock) stmt, counter);
        }
    }

    private void emitConsumerCall(final StringBuilder sb,
                                   final String methodPrefix,
                                   final int[] counter) {
        final int idx = counter[0]++;
        sb.append("  ((")
          .append(PACKAGE_PREFIX).append("BindingAware) this._consumer")
          .append(idx).append(").setBinding(binding);\n");
        sb.append("  ").append(methodPrefix)
          .append("(this._consumer").append(idx).append(");\n");
    }

    private void generateIfBlock(final StringBuilder sb,
                                  final LALScriptModel.IfBlock ifBlock,
                                  final int[] counter) {
        sb.append("  if (");
        generateCondition(sb, ifBlock.getCondition());
        sb.append(") {\n");
        for (final LALScriptModel.FilterStatement s : ifBlock.getThenBranch()) {
            generateFilterStatement(sb, s, counter);
        }
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            for (final LALScriptModel.FilterStatement s
                    : ifBlock.getElseBranch()) {
                generateFilterStatement(sb, s, counter);
            }
            sb.append("  }\n");
        }
    }

    // ==================== Helper methods ====================

    private void addHelperMethods(final CtClass ctClass) throws Exception {
        ctClass.addMethod(CtNewMethod.make(
            "private static Object getAt(Object obj, String key) {"
            + "  if (obj == null) return null;"
            + "  if (obj instanceof " + BINDING_PARSED + ")"
            + "    return ((" + BINDING_PARSED + ") obj).getAt(key);"
            + "  if (obj instanceof java.util.Map)"
            + "    return ((java.util.Map) obj).get(key);"
            + "  Object protoResult = " + BINDING_PARSED + ".getField(obj, key);"
            + "  if (protoResult != null) return protoResult;"
            + "  return null;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static long toLong(Object obj) {"
            + "  if (obj instanceof Number) return ((Number) obj).longValue();"
            + "  if (obj instanceof String) return Long.parseLong((String) obj);"
            + "  return 0L;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static int toInt(Object obj) {"
            + "  if (obj instanceof Number) return ((Number) obj).intValue();"
            + "  if (obj instanceof String) return Integer.parseInt((String) obj);"
            + "  return 0;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static String toStr(Object obj) {"
            + "  return obj == null ? null : String.valueOf(obj);"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static boolean toBool(Object obj) {"
            + "  if (obj instanceof Boolean) return ((Boolean) obj).booleanValue();"
            + "  if (obj instanceof String)"
            + " return Boolean.parseBoolean((String) obj);"
            + "  return obj != null;"
            + "}", ctClass));

        ctClass.addMethod(CtNewMethod.make(
            "private static boolean isTruthy(Object obj) {"
            + "  if (obj == null) return false;"
            + "  if (obj instanceof Boolean)"
            + " return ((Boolean) obj).booleanValue();"
            + "  if (obj instanceof String)"
            + " return !((String) obj).isEmpty();"
            + "  if (obj instanceof Number)"
            + " return ((Number) obj).doubleValue() != 0;"
            + "  return true;"
            + "}", ctClass));

        // tag() value lookup using Binding
        ctClass.addMethod(CtNewMethod.make(
            "private static String tagValue("
            + BINDING + " b, String key) {"
            + "  java.util.List dl = b.log().getTags().getDataList();"
            + "  for (int i = 0; i < dl.size(); i++) {"
            + "    org.apache.skywalking.apm.network.common.v3"
            + ".KeyStringValuePair kv = "
            + "(org.apache.skywalking.apm.network.common.v3"
            + ".KeyStringValuePair) dl.get(i);"
            + "    if (key.equals(kv.getKey())) return kv.getValue();"
            + "  }"
            + "  return \"\";"
            + "}", ctClass));

        // Safe method call helper
        ctClass.addMethod(CtNewMethod.make(
            "private static Object safeCall(Object obj, String method) {"
            + "  if (obj == null) return null;"
            + "  if (\"toString\".equals(method)) return obj.toString();"
            + "  if (\"trim\".equals(method)) return obj.toString().trim();"
            + "  if (\"isEmpty\".equals(method))"
            + " return Boolean.valueOf(obj.toString().isEmpty());"
            + "  return obj.toString();"
            + "}", ctClass));
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
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") > ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LT:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") < ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case GTE:
                    sb.append("toLong(");
                    generateValueAccessObj(sb, cc.getLeft(), null);
                    sb.append(") >= ");
                    generateConditionValueNumeric(sb, cc.getRight());
                    break;
                case LTE:
                    sb.append("toLong(");
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
            sb.append("isTruthy(");
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
            sb.append("toLong(");
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
            sb.append("toStr(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append("toLong(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Integer".equals(castType)) {
            sb.append("toInt(");
            generateValueAccess(sb, value);
            sb.append(")");
        } else if ("Boolean".equals(castType)) {
            sb.append("toBool(");
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
            sb.append("toStr(");
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
                sb.append("tagValue(binding, \"");
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
                current = "getAt(" + current + ", \""
                    + escapeJava(name) + "\")";
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms =
                    (LALScriptModel.MethodSegment) seg;
                if (ms.isSafeNav()) {
                    // Safe navigation: null-safe method call
                    current = "safeCall(" + current + ", \""
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
        final int[] counter = {0};
        return generateExecuteMethod(model, counter);
    }
}
