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

/**
 * Static code-generation methods for LAL extractor, sink, condition, and
 * value-access blocks. Extracted from {@link LALClassGenerator} for
 * readability; all methods are stateless and take a
 * {@link LALClassGenerator.GenCtx} parameter for shared state.
 */
final class LALBlockCodegen {

    private static final String FILTER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec";
    private static final String EXTRACTOR_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.ExtractorSpec";
    private static final String SAMPLE_BUILDER =
        EXTRACTOR_SPEC + "$SampleBuilder";
    private static final String H =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper";
    private static final String PROCESS_REGISTRY =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry";

    private LALBlockCodegen() {
        // utility class
    }

    // ==================== Extractor method generation ====================

    static void generateExtractorMethod(final StringBuilder sb,
                                         final LALScriptModel.ExtractorBlock block,
                                         final LALClassGenerator.GenCtx genCtx) {
        final String methodName = genCtx.nextMethodName("extractor");
        final Object[] savedState = genCtx.saveProtoVarState();
        genCtx.resetProtoVars();

        // Generate body first to collect proto var declarations
        final StringBuilder bodyContent = new StringBuilder();
        final List<LALScriptModel.FilterStatement> extractorStmts = new ArrayList<>();
        for (final LALScriptModel.ExtractorStatement es : block.getStatements()) {
            extractorStmts.add((LALScriptModel.FilterStatement) es);
        }
        generateExtractorBody(bodyContent, extractorStmts, genCtx);

        // Assemble method with declarations before body
        final StringBuilder body = new StringBuilder();
        body.append("private void ").append(methodName).append("(")
            .append(EXTRACTOR_SPEC).append(" _e, ").append(H).append(" h) {\n");

        final List<String[]> lvtVars = new ArrayList<>();
        lvtVars.add(new String[]{"_e", "L" + EXTRACTOR_SPEC.replace('.', '/') + ";"});
        lvtVars.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});

        if (genCtx.usedProtoAccess && genCtx.extraLogType != null) {
            final String elTypeName = genCtx.extraLogType.getName();
            body.append("  ").append(elTypeName).append(" _p = (")
                .append(elTypeName).append(") h.ctx().extraLog();\n");
            body.append(genCtx.protoVarDecls);
            lvtVars.add(new String[]{"_p", "L" + elTypeName.replace('.', '/') + ";"});
            lvtVars.addAll(genCtx.protoLvtVars);
        }

        body.append(bodyContent);
        body.append("}\n");
        genCtx.privateMethods.add(new LALClassGenerator.PrivateMethod(
            body.toString(), lvtVars.toArray(new String[0][])));

        genCtx.restoreProtoVarState(savedState);

        sb.append("  if (!ctx.shouldAbort()) {\n");
        sb.append("    ").append(methodName).append("(filterSpec.extractor(), h);\n");
        sb.append("  }\n");
    }

    static void generateExtractorBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final LALClassGenerator.GenCtx genCtx) {
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
                      .append(LALCodegenHelper.escapeJava(field.getFormatPattern()))
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

    static void generateIfBlockInExtractor(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateMetricsInline(
            final StringBuilder sb,
            final LALScriptModel.MetricsBlock block,
            final LALClassGenerator.GenCtx genCtx) {
        sb.append("  { ").append(SAMPLE_BUILDER).append(" _b = _e.prepareMetrics(h.ctx());\n");
        sb.append("  if (_b != null) {\n");
        if (block.getName() != null) {
            sb.append("  _b.name(\"")
                .append(LALCodegenHelper.escapeJava(block.getName())).append("\");\n");
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
                    .append(LALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
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

    static void generateSlowSqlInline(
            final StringBuilder sb,
            final LALScriptModel.SlowSqlBlock block,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSampledTraceInline(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceBlock block,
            final LALClassGenerator.GenCtx genCtx) {
        sb.append("  _e.prepareSampledTrace(h.ctx());\n");
        generateSampledTraceBody(sb, block.getStatements(), genCtx);
        sb.append("  _e.submitSampledTrace(h.ctx());\n");
    }

    static void generateSampledTraceBody(
            final StringBuilder sb,
            final List<LALScriptModel.SampledTraceStatement> stmts,
            final LALClassGenerator.GenCtx genCtx) {
        for (final LALScriptModel.SampledTraceStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SampledTraceField) {
                generateSampledTraceField(sb, (LALScriptModel.SampledTraceField) stmt,
                    genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSampledTraceIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    static void generateSampledTraceField(
            final StringBuilder sb,
            final LALScriptModel.SampledTraceField field,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSampledTraceIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSampledTraceBodyFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSampledTraceFieldFromAssignment(
            final StringBuilder sb,
            final LALScriptModel.FieldAssignment fa,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateTagAssignment(final StringBuilder sb,
                                       final LALScriptModel.TagAssignment tag,
                                       final LALClassGenerator.GenCtx genCtx) {
        for (final Map.Entry<String, LALScriptModel.TagValue> entry
                : tag.getTags().entrySet()) {
            sb.append("  _e.tag(h.ctx(), \"")
              .append(LALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
            generateStringValueAccess(sb, entry.getValue().getValue(),
                entry.getValue().getCastType(), genCtx);
            sb.append(");\n");
        }
    }

    // ==================== Sink method generation ====================

    static void generateSinkMethod(final StringBuilder sb,
                                    final LALScriptModel.SinkBlock sink,
                                    final LALClassGenerator.GenCtx genCtx) {
        final String methodName = genCtx.nextMethodName("sink");
        final Object[] savedState = genCtx.saveProtoVarState();
        genCtx.resetProtoVars();

        // Generate body first to collect proto var declarations
        final StringBuilder bodyContent = new StringBuilder();
        final List<LALScriptModel.FilterStatement> sinkStmts = new ArrayList<>();
        for (final LALScriptModel.SinkStatement ss : sink.getStatements()) {
            sinkStmts.add((LALScriptModel.FilterStatement) ss);
        }
        generateSinkBody(bodyContent, sinkStmts, genCtx);

        // Assemble method with declarations before body
        final StringBuilder body = new StringBuilder();
        body.append("private void ").append(methodName).append("(")
            .append(FILTER_SPEC).append(" _f, ").append(H).append(" h) {\n");

        final List<String[]> lvtVars = new ArrayList<>();
        lvtVars.add(new String[]{"_f", "L" + FILTER_SPEC.replace('.', '/') + ";"});
        lvtVars.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});

        if (genCtx.usedProtoAccess && genCtx.extraLogType != null) {
            final String elTypeName = genCtx.extraLogType.getName();
            body.append("  ").append(elTypeName).append(" _p = (")
                .append(elTypeName).append(") h.ctx().extraLog();\n");
            body.append(genCtx.protoVarDecls);
            lvtVars.add(new String[]{"_p", "L" + elTypeName.replace('.', '/') + ";"});
            lvtVars.addAll(genCtx.protoLvtVars);
        }

        body.append(bodyContent);
        body.append("}\n");
        genCtx.privateMethods.add(new LALClassGenerator.PrivateMethod(
            body.toString(), lvtVars.toArray(new String[0][])));

        genCtx.restoreProtoVarState(savedState);

        sb.append("  if (!ctx.shouldAbort()) {\n");
        sb.append("    ").append(methodName).append("(filterSpec, h);\n");
        sb.append("  }\n");
        sb.append("  filterSpec.finalizeSink(ctx);\n");
    }

    static void generateSinkBody(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateIfBlockInSink(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSamplerInline(
            final StringBuilder sb,
            final LALScriptModel.SamplerBlock block,
            final LALClassGenerator.GenCtx genCtx) {
        generateSamplerContents(sb, block.getContents(), genCtx);
    }

    static void generateSamplerContents(
            final StringBuilder sb,
            final List<LALScriptModel.SamplerContent> contents,
            final LALClassGenerator.GenCtx genCtx) {
        for (final LALScriptModel.SamplerContent content : contents) {
            if (content instanceof LALScriptModel.RateLimitBlock) {
                generateRateLimitInline(sb, (LALScriptModel.RateLimitBlock) content,
                    genCtx);
            } else if (content instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) content, genCtx);
            }
        }
    }

    static void generateSamplerIfBlock(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateSamplerContentsFromFilterStmts(
            final StringBuilder sb,
            final List<? extends LALScriptModel.FilterStatement> stmts,
            final LALClassGenerator.GenCtx genCtx) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.SamplerBlock) {
                generateSamplerContents(sb,
                    ((LALScriptModel.SamplerBlock) stmt).getContents(), genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            }
        }
    }

    static void generateRateLimitInline(
            final StringBuilder sb,
            final LALScriptModel.RateLimitBlock block,
            final LALClassGenerator.GenCtx genCtx) {
        sb.append("  _f.sampler().rateLimit(h.ctx(), ");
        if (block.isIdInterpolated()) {
            sb.append("\"\"");
            for (final LALScriptModel.InterpolationPart part : block.getIdParts()) {
                sb.append(" + ");
                if (part.isLiteral()) {
                    sb.append("\"").append(LALCodegenHelper.escapeJava(part.getLiteral()))
                      .append("\"");
                } else {
                    sb.append("String.valueOf(");
                    generateValueAccess(sb, part.getExpression(), genCtx);
                    sb.append(")");
                }
            }
        } else {
            sb.append("\"").append(LALCodegenHelper.escapeJava(block.getId())).append("\"");
        }
        sb.append(", ").append(block.getRpm()).append(");\n");
    }

    // ==================== Conditions ====================

    static void generateCondition(final StringBuilder sb,
                                   final LALScriptModel.Condition cond,
                                   final LALClassGenerator.GenCtx genCtx) {
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
                    generateNumericComparison(sb, cc, " > ", genCtx);
                    break;
                case LT:
                    generateNumericComparison(sb, cc, " < ", genCtx);
                    break;
                case GTE:
                    generateNumericComparison(sb, cc, " >= ", genCtx);
                    break;
                case LTE:
                    generateNumericComparison(sb, cc, " <= ", genCtx);
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

    static void generateNumericComparison(
            final StringBuilder sb,
            final LALScriptModel.ComparisonCondition cc,
            final String op,
            final LALClassGenerator.GenCtx genCtx) {
        // Generate left side into buffer to inspect resolved type
        final StringBuilder leftBuf = new StringBuilder();
        generateValueAccessObj(leftBuf, cc.getLeft(), null, genCtx);

        final boolean primitiveNumeric = genCtx.lastResolvedType != null
            && (genCtx.lastResolvedType == int.class
                || genCtx.lastResolvedType == long.class);

        if (primitiveNumeric && genCtx.lastRawChain != null) {
            // Direct primitive comparison — no boxing, no h.toLong()
            if (genCtx.lastNullChecks != null) {
                sb.append("(").append(genCtx.lastNullChecks).append(" ? false : ")
                  .append(genCtx.lastRawChain).append(op);
                generateConditionValueNumeric(sb, cc.getRight(), genCtx);
                sb.append(")");
            } else {
                sb.append(genCtx.lastRawChain).append(op);
                generateConditionValueNumeric(sb, cc.getRight(), genCtx);
            }
        } else {
            // Fallback: h.toLong() conversion
            sb.append("h.toLong(").append(leftBuf).append(")").append(op);
            generateConditionValueNumeric(sb, cc.getRight(), genCtx);
        }
    }

    static void generateConditionValue(final StringBuilder sb,
                                        final LALScriptModel.ConditionValue cv,
                                        final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.StringConditionValue) {
            sb.append('"')
              .append(LALCodegenHelper.escapeJava(
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

    static void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv,
            final LALClassGenerator.GenCtx genCtx) {
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

    static void generateCastedValueAccess(final StringBuilder sb,
                                           final LALScriptModel.ValueAccess value,
                                           final String castType,
                                           final LALClassGenerator.GenCtx genCtx) {
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

    static void generateStringValueAccess(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final String castType,
                                            final LALClassGenerator.GenCtx genCtx) {
        if (castType == null || "String".equals(castType)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append("String.valueOf(h.toLong(");
            generateValueAccess(sb, value, genCtx);
            sb.append("))");
        } else if ("Integer".equals(castType)) {
            sb.append("String.valueOf(h.toInt(");
            generateValueAccess(sb, value, genCtx);
            sb.append("))");
        } else if ("Boolean".equals(castType)) {
            sb.append("String.valueOf(h.toBool(");
            generateValueAccess(sb, value, genCtx);
            sb.append("))");
        } else {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        }
    }

    static void generateValueAccessObj(final StringBuilder sb,
                                        final LALScriptModel.ValueAccess value,
                                        final String castType,
                                        final LALClassGenerator.GenCtx genCtx) {
        if ("String".equals(castType)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    static void generateValueAccess(final StringBuilder sb,
                                     final LALScriptModel.ValueAccess value,
                                     final LALClassGenerator.GenCtx genCtx) {
        genCtx.clearExtraLogResult();

        // Handle string concatenation (term1 + term2 + ...)
        if (!value.getConcatParts().isEmpty()) {
            sb.append("(\"\" + ");
            for (int i = 0; i < value.getConcatParts().size(); i++) {
                if (i > 0) {
                    sb.append(" + ");
                }
                generateValueAccess(sb, value.getConcatParts().get(i), genCtx);
            }
            sb.append(")");
            return;
        }

        // Handle parenthesized expression: (innerExpr as Type).chain...
        if (value.getParenInner() != null) {
            generateParenAccess(sb, value, genCtx);
            return;
        }

        // Handle function call primaries (e.g., tag("LOG_KIND"))
        if (value.getFunctionCallName() != null) {
            if ("tag".equals(value.getFunctionCallName())
                    && !value.getFunctionCallArgs().isEmpty()) {
                sb.append("h.tagValue(\"");
                final String key = value.getFunctionCallArgs().get(0)
                    .getValue().getSegments().get(0);
                sb.append(LALCodegenHelper.escapeJava(key)).append("\")");
            } else {
                sb.append("null");
            }
            return;
        }

        // Handle string/number literals
        if (value.isStringLiteral() && value.getChain().isEmpty()) {
            sb.append("\"").append(LALCodegenHelper.escapeJava(value.getSegments().get(0)))
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

    // ==================== Parenthesized expression ====================

    static void generateParenAccess(final StringBuilder sb,
                                     final LALScriptModel.ValueAccess value,
                                     final LALClassGenerator.GenCtx genCtx) {
        // Generate the inner expression with cast
        final String castType = value.getParenCast();
        final StringBuilder inner = new StringBuilder();
        if (castType != null) {
            generateCastedValueAccess(inner, value.getParenInner(), castType, genCtx);
        } else {
            generateValueAccess(inner, value.getParenInner(), genCtx);
        }

        // Apply chain segments (methods, fields, index access)
        String current = inner.toString();
        for (final LALScriptModel.ValueAccessSegment seg : value.getChain()) {
            if (seg instanceof LALScriptModel.MethodSegment) {
                current = appendMethodSegment(current,
                    (LALScriptModel.MethodSegment) seg);
            } else if (seg instanceof LALScriptModel.IndexSegment) {
                current = current + "["
                    + ((LALScriptModel.IndexSegment) seg).getIndex() + "]";
            } else if (seg instanceof LALScriptModel.FieldSegment) {
                final LALScriptModel.FieldSegment fs =
                    (LALScriptModel.FieldSegment) seg;
                if (fs.isSafeNav()) {
                    current = "(" + current + " == null ? null : "
                        + current + "." + fs.getName() + ")";
                } else {
                    current = current + "." + fs.getName();
                }
            }
        }
        sb.append(current);
    }

    // ==================== Log access (direct proto getters) ====================

    static void generateLogAccess(final StringBuilder sb,
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
                if (i == 0 && LALCodegenHelper.LOG_GETTERS.containsKey(name)) {
                    if ("traceContext".equals(name)) {
                        current = current + ".getTraceContext()";
                    } else {
                        current = current + "."
                            + LALCodegenHelper.LOG_GETTERS.get(name) + "()";
                        if (LALCodegenHelper.LONG_FIELDS.contains(name)) {
                            needsBoxing = true;
                            boxType = "Long";
                        }
                    }
                } else if (i == 1 && current.endsWith(".getTraceContext()")
                        && LALCodegenHelper.TRACE_CONTEXT_GETTERS.containsKey(name)) {
                    current = current + "."
                        + LALCodegenHelper.TRACE_CONTEXT_GETTERS.get(name) + "()";
                    if (LALCodegenHelper.INT_FIELDS.contains(name)) {
                        needsBoxing = true;
                        boxType = "Integer";
                    }
                } else {
                    throw new IllegalArgumentException(
                        "Unknown log field: log." + name
                            + ". Supported fields: "
                            + LALCodegenHelper.LOG_GETTERS.keySet()
                            + ", traceContext."
                            + LALCodegenHelper.TRACE_CONTEXT_GETTERS.keySet());
                }
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                current = appendMethodSegment(current,
                    (LALScriptModel.MethodSegment) seg);
            }
        }

        if (needsBoxing) {
            sb.append(boxType).append(".valueOf(").append(current).append(")");
        } else {
            sb.append(current);
        }
    }

    // ==================== Parsed access (compile-time typed) ====================

    static void generateParsedAccess(
            final StringBuilder sb,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append("h.ctx().parsed()");
            return;
        }

        // Collect leading field segments (stop at method/index)
        final List<LALScriptModel.FieldSegment> fieldSegments = new ArrayList<>();
        int methodStart = -1;
        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                fieldSegments.add((LALScriptModel.FieldSegment) seg);
            } else {
                methodStart = i;
                break;
            }
        }

        final List<String> fieldKeys = new ArrayList<>();
        for (final LALScriptModel.FieldSegment fs : fieldSegments) {
            fieldKeys.add(fs.getName());
        }

        String current;
        switch (genCtx.parserType) {
            case JSON:
            case YAML:
                current = LALCodegenHelper.generateMapValCall(fieldKeys);
                break;
            case TEXT:
                if (!fieldKeys.isEmpty()) {
                    current = "h.group(\""
                        + LALCodegenHelper.escapeJava(fieldKeys.get(0)) + "\")";
                } else {
                    current = "h.ctx().parsed()";
                }
                break;
            case NONE:
                if (genCtx.extraLogType != null) {
                    current = generateExtraLogAccess(fieldSegments, genCtx.extraLogType,
                        genCtx);
                } else {
                    throw new IllegalStateException(
                        "LAL rule accesses parsed.* fields ("
                            + String.join(".", fieldKeys)
                            + ") but type is unknown — no parser (json/yaml/text), "
                            + "no extraLogType in YAML config, and no "
                            + "LALSourceTypeProvider SPI registered for this layer. "
                            + "Either add a parser to the DSL, declare extraLogType "
                            + "in the YAML config, or register an SPI provider in "
                            + "the receiver plugin.");
                }
                break;
            default:
                current = "null";
                break;
        }

        // Apply remaining method/index segments
        if (methodStart >= 0) {
            for (int i = methodStart; i < chain.size(); i++) {
                final LALScriptModel.ValueAccessSegment seg = chain.get(i);
                if (seg instanceof LALScriptModel.MethodSegment) {
                    current = appendMethodSegment(current,
                        (LALScriptModel.MethodSegment) seg);
                } else if (seg instanceof LALScriptModel.IndexSegment) {
                    current = current + "["
                        + ((LALScriptModel.IndexSegment) seg).getIndex() + "]";
                } else if (seg instanceof LALScriptModel.FieldSegment) {
                    current = current + "."
                        + ((LALScriptModel.FieldSegment) seg).getName();
                }
            }
        }

        sb.append(current);
    }

    static String generateExtraLogAccess(
            final List<LALScriptModel.FieldSegment> fieldSegments,
            final Class<?> extraLogType,
            final LALClassGenerator.GenCtx genCtx) {
        genCtx.usedProtoAccess = true;

        if (fieldSegments.isEmpty()) {
            return "_p";
        }

        final String typeName = extraLogType.getName();
        final StringBuilder chainKey = new StringBuilder();
        String prevVar = "_p";
        Class<?> currentType = extraLogType;
        boolean prevCanBeNull = true;

        for (int i = 0; i < fieldSegments.size(); i++) {
            final LALScriptModel.FieldSegment seg = fieldSegments.get(i);
            final String field = seg.getName();
            final String getterName = "get" + Character.toUpperCase(field.charAt(0))
                + field.substring(1);

            final java.lang.reflect.Method getter;
            try {
                getter = currentType.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "Cannot resolve getter " + currentType.getSimpleName()
                        + "." + getterName + "() for extraLogType "
                        + typeName + ". Check the field path in the LAL rule.");
            }
            final Class<?> returnType = getter.getReturnType();

            if (chainKey.length() > 0) {
                chainKey.append(".");
            }
            chainKey.append(field);
            final String key = chainKey.toString();
            final boolean isLast = i == fieldSegments.size() - 1;

            // Primitive final segment: return inline expression, no variable
            if (isLast && returnType.isPrimitive()) {
                final String rawAccess = prevVar + "." + getterName + "()";
                genCtx.lastResolvedType = returnType;
                genCtx.lastRawChain = rawAccess;
                final String boxName = LALCodegenHelper.boxTypeName(returnType);
                if (seg.isSafeNav() && prevCanBeNull) {
                    genCtx.lastNullChecks = prevVar + " == null";
                    return "(" + prevVar + " == null ? null : "
                        + boxName + ".valueOf(" + rawAccess + "))";
                } else {
                    genCtx.lastNullChecks = null;
                    return boxName + ".valueOf(" + rawAccess + ")";
                }
            }

            // Reuse existing variable (dedup)
            final String existingVar = genCtx.protoVars.get(key);
            if (existingVar != null) {
                prevVar = existingVar;
                currentType = returnType;
                prevCanBeNull = true;
                continue;
            }

            // Create new local variable declaration
            final String newVar = "_t" + genCtx.protoVarCounter++;
            final String returnTypeName = returnType.getName();
            if (seg.isSafeNav() && prevCanBeNull) {
                genCtx.protoVarDecls.append("  ").append(returnTypeName)
                    .append(" ").append(newVar).append(" = ")
                    .append(prevVar).append(" == null ? null : ")
                    .append(prevVar).append(".").append(getterName).append("();\n");
                prevCanBeNull = true;
            } else {
                genCtx.protoVarDecls.append("  ").append(returnTypeName)
                    .append(" ").append(newVar).append(" = ")
                    .append(prevVar).append(".").append(getterName).append("();\n");
                prevCanBeNull = !returnType.isPrimitive();
            }
            genCtx.protoVars.put(key, newVar);
            genCtx.protoLvtVars.add(new String[]{
                newVar, "L" + returnTypeName.replace('.', '/') + ";"
            });

            prevVar = newVar;
            currentType = returnType;
        }

        // Non-primitive final result — null checks are in declarations
        genCtx.lastResolvedType = currentType;
        genCtx.lastRawChain = prevVar;
        genCtx.lastNullChecks = null;
        return prevVar;
    }

    // ==================== ProcessRegistry ====================

    static void generateProcessRegistryCall(
            final StringBuilder sb,
            final LALScriptModel.ValueAccess value,
            final LALClassGenerator.GenCtx genCtx) {
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

    // ==================== Utility methods ====================

    static String appendMethodSegment(final String current,
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

    static String generateMethodArgs(
            final List<LALScriptModel.FunctionArg> args) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final LALScriptModel.FunctionArg arg = args.get(i);
            final LALScriptModel.ValueAccess va = arg.getValue();
            if (va.isStringLiteral()) {
                sb.append("\"").append(LALCodegenHelper.escapeJava(
                    va.getSegments().get(0))).append("\"");
            } else if (va.isNumberLiteral()) {
                sb.append(va.getSegments().get(0));
            } else {
                sb.append("null");
            }
        }
        return sb.toString();
    }
}
