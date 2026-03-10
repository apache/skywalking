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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.logging.v3.LogData;

/**
 * Static code-generation methods for LAL extractor, sink, condition, and
 * value-access blocks. Extracted from {@link LALClassGenerator} for
 * readability; all methods are stateless and take a
 * {@link LALClassGenerator.GenCtx} parameter for shared state.
 */
final class LALBlockCodegen {

    private static final String FILTER_SPEC =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec";
    private static final String METRIC_EXTRACTOR =
        "org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.MetricExtractor";
    private static final String SAMPLE_BUILDER =
        METRIC_EXTRACTOR + "$SampleBuilder";
    private static final String H =
        "org.apache.skywalking.oap.log.analyzer.v2.compiler.rt.LalRuntimeHelper";
    private static final String PROCESS_REGISTRY =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry";

    // Built-in function registry for def variable type inference.
    // Maps DSL function name → [runtime helper method, return type].
    static final Map<String, Object[]> BUILTIN_FUNCTIONS = new HashMap<>();

    static {
        BUILTIN_FUNCTIONS.put("toJson", new Object[]{"h.toJsonObject", JsonObject.class});
        BUILTIN_FUNCTIONS.put("toJsonArray", new Object[]{"h.toJsonArray", JsonArray.class});
    }

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
            .append(METRIC_EXTRACTOR).append(" _e, ").append(H).append(" h) {\n");

        final List<String[]> lvtVars = new ArrayList<>();
        lvtVars.add(new String[]{"_e", "L" + METRIC_EXTRACTOR.replace('.', '/') + ";"});
        lvtVars.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});

        if (genCtx.usedProtoAccess) {
            if (genCtx.inputType != null) {
                final String elTypeName = genCtx.inputType.getName();
                body.append("  ").append(elTypeName).append(" _p = (")
                    .append(elTypeName).append(") h.ctx().extraLog();\n");
                lvtVars.add(new String[]{"_p",
                    "L" + elTypeName.replace('.', '/') + ";"});
            }
            body.append(genCtx.protoVarDecls);
            lvtVars.addAll(genCtx.protoLvtVars);
        }

        // Cast output once if extractor uses the output object
        if (genCtx.outputType != null && hasOutputAccess(block.getStatements())) {
            final String outTypeName = genCtx.outputType.getName();
            body.append("  ").append(outTypeName).append(" _o = (")
                .append(outTypeName).append(") h.ctx().output();\n");
            lvtVars.add(new String[]{"_o",
                "L" + outTypeName.replace('.', '/') + ";"});
        }

        // Add local var declarations from def statements
        if (genCtx.localVarDecls.length() > 0) {
            body.append(genCtx.localVarDecls);
            lvtVars.addAll(genCtx.localVarLvtVars);
        }

        // Add LVT entry for _metrics if any metrics block exists
        if (hasMetricsBlock(block.getStatements())) {
            lvtVars.add(new String[]{"_metrics",
                "L" + SAMPLE_BUILDER.replace('.', '/') + ";"});
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
                generateFieldToOutput(sb, field, genCtx);
            } else if (stmt instanceof LALScriptModel.TagAssignment) {
                generateTagAssignment(sb, (LALScriptModel.TagAssignment) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateIfBlockInExtractor(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.MetricsBlock) {
                generateMetricsInline(sb, (LALScriptModel.MetricsBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.OutputFieldAssignment) {
                generateOutputFieldAssignment(
                    sb, (LALScriptModel.OutputFieldAssignment) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.DefStatement) {
                generateDefStatement(
                    sb, (LALScriptModel.DefStatement) stmt, genCtx);
            }
        }
    }

    private static final String[][] FIELD_TYPE_SETTER_CANDIDATES = {
        // SERVICE
        {"setServiceName", "setService"},
        // INSTANCE
        {"setServiceInstanceName", "setServiceInstance", "setInstance"},
        // ENDPOINT
        {"setEndpoint"},
        // LAYER
        {"setLayer"},
        // TRACE_ID
        {"setTraceId"},
        // SEGMENT_ID
        {"setSegmentId"},
        // SPAN_ID
        {"setSpanId"},
        // TIMESTAMP
        {"setTimestamp"},
    };

    private static void generateFieldToOutput(
            final StringBuilder sb,
            final LALScriptModel.FieldAssignment field,
            final LALClassGenerator.GenCtx genCtx) {
        final String[] candidates =
            FIELD_TYPE_SETTER_CANDIDATES[field.getFieldType().ordinal()];
        java.lang.reflect.Method setter = null;
        for (final String candidate : candidates) {
            setter = findSetter(genCtx.outputType, candidate);
            if (setter != null) {
                break;
            }
        }
        if (setter == null) {
            throw new IllegalArgumentException(
                "Output type " + genCtx.outputType.getName()
                + " has no setter for field '" + field.getFieldType().name().toLowerCase()
                + "' (tried: " + String.join(", ", candidates) + ")");
        }

        final Class<?> paramType = setter.getParameterTypes()[0];
        final String effectiveCast = resolveEffectiveCast(paramType, field.getCastType());
        sb.append("  _o.").append(setter.getName()).append("(");
        if (field.getFormatPattern() != null) {
            // Format pattern provided in LAL script (e.g., timestamp ... , "yyyy/MM/dd HH:mm:ss")
            sb.append("h.parseTimestamp(");
            generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(", \"")
              .append(LALCodegenHelper.escapeJava(field.getFormatPattern()))
              .append("\")");
        } else if (paramType.isEnum()) {
            sb.append(paramType.getName()).append(".valueOf(");
            generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(")");
        } else {
            generateCastedValueAccess(sb, field.getValue(), effectiveCast, genCtx);
        }
        sb.append(");\n");
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
        sb.append("  { ").append(SAMPLE_BUILDER).append(" _metrics = _e.prepareMetrics(h.ctx());\n");
        sb.append("  if (_metrics != null) {\n");
        if (block.getName() != null) {
            sb.append("  _metrics.name(\"")
                .append(LALCodegenHelper.escapeJava(block.getName())).append("\");\n");
        }
        if (block.getTimestampValue() != null) {
            sb.append("  _metrics.timestamp(");
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
            sb.append("    _metrics.labels(_labels); }\n");
        }
        if (block.getValue() != null) {
            sb.append("  _metrics.value(");
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
        sb.append("  _e.submitMetrics(h.ctx(), _metrics);\n");
        sb.append("  } }\n");
    }

    // ==================== Tag assignment ====================

    static void generateTagAssignment(final StringBuilder sb,
                                       final LALScriptModel.TagAssignment tag,
                                       final LALClassGenerator.GenCtx genCtx) {
        // tag assignments are only supported on LogBuilder (the default output type).
        // Other output types (e.g. SampledTraceBuilder, DatabaseSlowStatementBuilder)
        // do not carry tags — fail at compile time instead of silently dropping them.
        if (genCtx.outputType != null
                && !org.apache.skywalking.oap.server.core.source.LogBuilder.class
                        .isAssignableFrom(genCtx.outputType)) {
            throw new IllegalArgumentException(
                "LAL 'tag' assignments are only supported when outputType is LogBuilder"
                    + " (or default), but the resolved outputType is "
                    + genCtx.outputType.getName()
                    + ". Remove the 'tag' statements or change the outputType.");
        }
        for (final Map.Entry<String, LALScriptModel.TagValue> entry
                : tag.getTags().entrySet()) {
            sb.append("  _o.addTag(\"")
              .append(LALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
            generateStringValueAccess(sb, entry.getValue().getValue(),
                entry.getValue().getCastType(), genCtx);
            sb.append(");\n");
        }
    }

    // ==================== Output field assignment ====================

    private static boolean hasMetricsBlock(
            final List<? extends LALScriptModel.ExtractorStatement> stmts) {
        for (final LALScriptModel.ExtractorStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.MetricsBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOutputAccess(
            final List<? extends LALScriptModel.ExtractorStatement> stmts) {
        for (final LALScriptModel.ExtractorStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.OutputFieldAssignment
                    || stmt instanceof LALScriptModel.FieldAssignment
                    || stmt instanceof LALScriptModel.TagAssignment) {
                return true;
            }
            if (stmt instanceof LALScriptModel.IfBlock) {
                final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
                if (hasOutputAccessInFilterStmts(ifBlock.getThenBranch())
                        || hasOutputAccessInFilterStmts(ifBlock.getElseBranch())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOutputAccessInFilterStmts(
            final List<? extends LALScriptModel.FilterStatement> stmts) {
        for (final LALScriptModel.FilterStatement stmt : stmts) {
            if (stmt instanceof LALScriptModel.OutputFieldAssignment
                    || stmt instanceof LALScriptModel.FieldAssignment
                    || stmt instanceof LALScriptModel.TagAssignment) {
                return true;
            }
            if (stmt instanceof LALScriptModel.IfBlock) {
                final LALScriptModel.IfBlock ifBlock = (LALScriptModel.IfBlock) stmt;
                if (hasOutputAccessInFilterStmts(ifBlock.getThenBranch())
                        || hasOutputAccessInFilterStmts(ifBlock.getElseBranch())) {
                    return true;
                }
            }
        }
        return false;
    }

    static void generateOutputFieldAssignment(
            final StringBuilder sb,
            final LALScriptModel.OutputFieldAssignment field,
            final LALClassGenerator.GenCtx genCtx) {
        final String fieldName = field.getFieldName();
        final String setterName = "set"
            + Character.toUpperCase(fieldName.charAt(0))
            + fieldName.substring(1);

        if (genCtx.outputType == null) {
            throw new IllegalArgumentException(
                "Output field '" + fieldName + "' requires outputType to be set in the LAL rule config");
        }

        // Compile-time validation: verify the setter exists on the output type
        final java.lang.reflect.Method setter = findSetter(genCtx.outputType, setterName);
        if (setter == null) {
            throw new IllegalArgumentException(
                "Output type " + genCtx.outputType.getName()
                + " has no setter " + setterName
                + "() for output field '" + fieldName + "'");
        }

        // Generate direct setter call: _o.setXxx(value)
        // _o is declared once at the top of the extractor method
        final Class<?> paramType = setter.getParameterTypes()[0];
        sb.append("  _o.").append(setterName).append("(");
        final String effectiveCast = resolveEffectiveCast(paramType, field.getCastType());
        if (paramType.isEnum()) {
            // Auto-convert String to enum: EnumType.valueOf(stringValue.toUpperCase())
            // toUpperCase() handles case-insensitive matching (e.g., "slow" → "SLOW")
            sb.append(paramType.getName()).append(".valueOf(((String) ");
            generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(").toUpperCase())");
        } else {
            generateCastedValueAccess(sb, field.getValue(), effectiveCast, genCtx);
        }
        sb.append(");\n");
    }

    private static java.lang.reflect.Method findSetter(
            final Class<?> clazz, final String setterName) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (final java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(setterName)
                        && m.getParameterCount() == 1) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Resolve the effective cast type based on the setter parameter type.
     * When the setter takes a primitive, use the matching cast so that
     * Javassist can resolve the correct overload (e.g. setComponentId(int)
     * not setComponentId(Integer)).
     */
    private static String resolveEffectiveCast(final Class<?> paramType,
                                                final String lalCast) {
        if (paramType == long.class) {
            return "Long";
        }
        if (paramType == int.class) {
            return "Integer";
        }
        if (paramType == double.class || paramType == float.class) {
            return "Long"; // toLong handles numeric conversion
        }
        if (paramType == boolean.class) {
            return "Boolean";
        }
        if (paramType == String.class) {
            return "String";
        }
        // For boxed types and others, use the LAL-declared cast
        return lalCast;
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

        if (genCtx.usedProtoAccess) {
            if (genCtx.inputType != null) {
                final String elTypeName = genCtx.inputType.getName();
                body.append("  ").append(elTypeName).append(" _p = (")
                    .append(elTypeName).append(") h.ctx().extraLog();\n");
                lvtVars.add(new String[]{"_p",
                    "L" + elTypeName.replace('.', '/') + ";"});
            }
            body.append(genCtx.protoVarDecls);
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

        // Check for def variable reference
        if (!value.getSegments().isEmpty()) {
            final String primaryName = value.getSegments().get(0);
            final LALClassGenerator.LocalVarInfo localVar =
                genCtx.localVars.get(primaryName);
            if (localVar != null) {
                generateDefVarChain(sb, localVar, chain, genCtx);
                return;
            }
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
                    (LALScriptModel.MethodSegment) seg, genCtx);
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
                    (LALScriptModel.MethodSegment) seg, null);
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
                if (genCtx.inputType != null) {
                    current = generateExtraLogAccess(fieldSegments, genCtx.inputType,
                        "_p", true, genCtx);
                } else {
                    // No parser and no inputType — fall back to LogData proto
                    current = generateExtraLogAccess(fieldSegments, LogData.Builder.class,
                        "h.ctx().log()", false, genCtx);
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
                        (LALScriptModel.MethodSegment) seg, genCtx);
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
            final Class<?> rootType,
            final String rootExpr,
            final boolean rootCanBeNull,
            final LALClassGenerator.GenCtx genCtx) {
        genCtx.usedProtoAccess = true;

        if (fieldSegments.isEmpty()) {
            return rootExpr;
        }

        final String typeName = rootType.getName();
        final StringBuilder chainKey = new StringBuilder();
        String prevVar = rootExpr;
        Class<?> currentType = rootType;
        boolean prevCanBeNull = rootCanBeNull;

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
                        + "." + getterName + "() for type "
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

    // ==================== Def statement codegen ====================

    static void generateDefStatement(final StringBuilder sb,
                                      final LALScriptModel.DefStatement def,
                                      final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess init = def.getInitializer();
        final String varName = def.getVarName();
        final String javaVar = "_" + varName;
        final boolean alreadyDeclared = genCtx.localVars.containsKey(varName);

        // Determine type and generate initializer expression
        Class<?> resolvedType;
        final StringBuilder initExpr = new StringBuilder();

        if (init.getFunctionCallName() != null
                && BUILTIN_FUNCTIONS.containsKey(init.getFunctionCallName())) {
            // Built-in function: toJson(...), toJsonArray(...)
            final String funcName = init.getFunctionCallName();
            final int argCount = init.getFunctionCallArgs().size();
            if (argCount != 1) {
                throw new IllegalArgumentException(
                    funcName + "() requires exactly 1 argument, got " + argCount);
            }
            final Object[] info = BUILTIN_FUNCTIONS.get(funcName);
            final String helperMethod = (String) info[0];
            resolvedType = (Class<?>) info[1];

            initExpr.append(helperMethod).append("(");
            generateValueAccess(initExpr,
                init.getFunctionCallArgs().get(0).getValue(), genCtx);
            initExpr.append(")");
        } else {
            // General value access — type inferred from lastResolvedType
            generateValueAccess(initExpr, init, genCtx);
            resolvedType = genCtx.lastResolvedType != null
                ? genCtx.lastResolvedType : Object.class;
            // Box primitive types for local variable declarations
            if (resolvedType.isPrimitive()) {
                final String boxName = LALCodegenHelper.boxTypeName(resolvedType);
                if (boxName != null) {
                    try {
                        resolvedType = Class.forName("java.lang." + boxName);
                    } catch (ClassNotFoundException ignored) {
                        // keep primitive
                    }
                }
            }
        }

        // Apply explicit type cast if specified (e.g., "as com.example.MyType")
        final String castType = def.getCastType();
        if (castType != null && !castType.isEmpty()) {
            // Resolve the cast type — primitive wrapper names are handled,
            // anything else is treated as a FQCN
            final Class<?> castClass = resolveDefCastType(castType);
            if (castClass != null) {
                resolvedType = castClass;
            }
        }

        // Register in local vars for later reference
        genCtx.localVars.put(varName,
            new LALClassGenerator.LocalVarInfo(javaVar, resolvedType));

        // Emit declaration (placed at method top via localVarDecls) — skip if already declared
        if (!alreadyDeclared) {
            genCtx.localVarDecls.append("  ").append(resolvedType.getName())
                .append(" ").append(javaVar).append(";\n");
            genCtx.localVarLvtVars.add(new String[]{
                javaVar, "L" + resolvedType.getName().replace('.', '/') + ";"
            });
        }

        // Emit assignment in body (at the point where def appears)
        sb.append("  ").append(javaVar).append(" = ");
        if (castType != null && !castType.isEmpty()) {
            sb.append("(").append(resolvedType.getName()).append(") ");
        }
        sb.append(initExpr).append(";\n");
    }

    /**
     * Resolves a cast type string to a {@link Class}.
     * Handles the four built-in type names ({@code String}, {@code Long},
     * {@code Integer}, {@code Boolean}) and fully qualified class names.
     */
    private static Class<?> resolveDefCastType(final String castType) {
        switch (castType) {
            case "String":
                return String.class;
            case "Long":
                return Long.class;
            case "Integer":
                return Integer.class;
            case "Boolean":
                return Boolean.class;
            default:
                try {
                    return Class.forName(castType);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(
                        "def cast type not found on classpath: " + castType, e);
                }
        }
    }

    // ==================== Def variable chain codegen ====================

    /**
     * Generates typed method-chain access on a def variable.
     * Uses reflection to resolve each method/field call and track types.
     *
     * @param sb output buffer
     * @param localVar the def variable info (java var name + resolved type)
     * @param chain the chain segments after the variable name
     * @param genCtx codegen context
     */
    static void generateDefVarChain(
            final StringBuilder sb,
            final LALClassGenerator.LocalVarInfo localVar,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append(localVar.javaVarName);
            genCtx.lastResolvedType = localVar.resolvedType;
            return;
        }

        String prevExpr = localVar.javaVarName;
        Class<?> currentType = localVar.resolvedType;
        boolean canBeNull = true;

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            final boolean isLast = i == chain.size() - 1;

            if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms =
                    (LALScriptModel.MethodSegment) seg;
                final String methodName = ms.getName();

                // Resolve method on currentType via reflection
                final java.lang.reflect.Method method =
                    resolveMethod(currentType, methodName, ms.getArguments());
                if (method == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve method " + currentType.getSimpleName()
                            + "." + methodName + "() in def variable chain");
                }
                final Class<?> returnType = method.getReturnType();
                final String args = generateMethodArgs(ms.getArguments(), genCtx);

                if (ms.isSafeNav() && canBeNull) {
                    if (isLast && returnType.isPrimitive()) {
                        // Primitive return with null guard
                        final String boxName =
                            LALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + methodName + "(" + args + ")))";
                        currentType = returnType;
                    } else {
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + prevExpr + "." + methodName + "(" + args + "))";
                        currentType = returnType;
                        canBeNull = true;
                    }
                } else {
                    prevExpr = prevExpr + "." + methodName + "(" + args + ")";
                    currentType = returnType;
                    canBeNull = !returnType.isPrimitive();
                }
            } else if (seg instanceof LALScriptModel.FieldSegment) {
                final LALScriptModel.FieldSegment fs =
                    (LALScriptModel.FieldSegment) seg;
                final String fieldName = fs.getName();
                // Try getter first
                final String getterName = "get"
                    + Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1);
                java.lang.reflect.Method getter = null;
                try {
                    getter = currentType.getMethod(getterName);
                } catch (NoSuchMethodException e) {
                    // Try direct field access name
                    try {
                        getter = currentType.getMethod(fieldName);
                    } catch (NoSuchMethodException e2) {
                        throw new IllegalArgumentException(
                            "Cannot resolve field/getter "
                                + currentType.getSimpleName()
                                + "." + fieldName + " in def variable chain");
                    }
                }
                final Class<?> returnType = getter.getReturnType();

                if (fs.isSafeNav() && canBeNull) {
                    if (isLast && returnType.isPrimitive()) {
                        final String boxName =
                            LALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + getter.getName() + "()))";
                        currentType = returnType;
                    } else {
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + prevExpr + "." + getter.getName() + "())";
                        currentType = returnType;
                        canBeNull = true;
                    }
                } else {
                    prevExpr = prevExpr + "." + getter.getName() + "()";
                    currentType = returnType;
                    canBeNull = !returnType.isPrimitive();
                }
            } else if (seg instanceof LALScriptModel.IndexSegment) {
                final int index = ((LALScriptModel.IndexSegment) seg).getIndex();
                // Try get(int) method (e.g., JsonArray.get(int))
                java.lang.reflect.Method getMethod = null;
                try {
                    getMethod = currentType.getMethod("get", int.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                        "Cannot resolve index access on "
                            + currentType.getSimpleName()
                            + " in def variable chain");
                }
                final Class<?> returnType = getMethod.getReturnType();
                if (canBeNull) {
                    prevExpr = "(" + prevExpr + " == null ? null : "
                        + prevExpr + ".get(" + index + "))";
                } else {
                    prevExpr = prevExpr + ".get(" + index + ")";
                }
                currentType = returnType;
                canBeNull = true;
            }
        }

        genCtx.lastResolvedType = currentType;
        sb.append(prevExpr);
    }

    /**
     * Resolves a method on the given type by name, matching argument count.
     * For methods with String arguments (like JsonObject.get(String)),
     * prioritizes exact match by parameter types.
     */
    private static java.lang.reflect.Method resolveMethod(
            final Class<?> type, final String name,
            final List<LALScriptModel.FunctionArg> args) {
        final int argCount = args != null ? args.size() : 0;
        // Try exact match with common parameter types
        if (argCount == 1) {
            try {
                return type.getMethod(name, String.class);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
            try {
                return type.getMethod(name, int.class);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        if (argCount == 0) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        // Fallback: find by name and arg count
        for (final java.lang.reflect.Method m : type.getMethods()) {
            if (m.getName().equals(name)
                    && m.getParameterCount() == argCount) {
                return m;
            }
        }
        return null;
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
                                       final LALScriptModel.MethodSegment ms,
                                       final LALClassGenerator.GenCtx genCtx) {
        final String mn = ms.getName();
        final String args = ms.getArguments().isEmpty()
            ? "" : generateMethodArgs(ms.getArguments(), genCtx);
        if (ms.isSafeNav()) {
            // Special-cased helpers for common safe-nav methods on Object
            if ("toString".equals(mn)) {
                return "h.toString(" + current + ")";
            } else if ("trim".equals(mn)) {
                return "h.trim(" + current + ")";
            }
            // General safe-nav: null guard with ternary
            return "(" + current + " == null ? null : "
                + current + "." + mn + "(" + args + "))";
        } else {
            return current + "." + mn + "(" + args + ")";
        }
    }

    static String generateMethodArgs(
            final List<LALScriptModel.FunctionArg> args,
            final LALClassGenerator.GenCtx genCtx) {
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
            } else if (!va.getSegments().isEmpty()) {
                final String text = va.getSegments().get(0);
                if ("true".equals(text) || "false".equals(text)
                        || "null".equals(text)) {
                    // Boolean or null literal
                    sb.append(text);
                } else if (genCtx != null
                        && genCtx.localVars.containsKey(text)) {
                    // Local def variable reference
                    sb.append(genCtx.localVars.get(text).javaVarName);
                } else {
                    sb.append("null");
                }
            } else {
                sb.append("null");
            }
        }
        return sb.toString();
    }
}
