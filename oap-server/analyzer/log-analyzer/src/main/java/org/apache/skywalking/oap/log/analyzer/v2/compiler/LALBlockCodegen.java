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

/**
 * Code generation for LAL block-level structures: {@code extractor},
 * {@code sink}, {@code sampler}, {@code metrics}, tag assignments, and
 * output-field assignments.
 *
 * <p>Given a LAL script:
 * <pre>{@code
 * filter {
 *   json {}
 *   extractor {                          // → generateExtractorMethod
 *     service parsed.service as String   //   → generateFieldToOutput
 *     tag 'status.code': parsed.code     //   → generateTagAssignment
 *     latency parsed.latency as Long     //   → generateOutputFieldAssignment
 *     if (tag("LOG_KIND") == "SLOW") {   //   → generateIfBlockInExtractor
 *       ...
 *     }
 *     metrics {                          //   → generateMetricsInline
 *       name "log_count"
 *       value 1
 *     }
 *   }
 *   sink {                               // → generateSinkMethod
 *     sampler {                          //   → generateSamplerInline
 *       rateLimit("${log.service}") {    //     → generateRateLimitInline
 *         rpm 6000
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Each block becomes a private method in the generated class (e.g.
 * {@code _extractor()}, {@code _sink()}). This class emits the method
 * scaffolding (signature, local variable declarations, LVT entries) and
 * walks the AST statements within each block.
 *
 * <p>Expression-level codegen (value access, conditions, type casts) is
 * delegated to {@link LALValueCodegen}. {@code def} variable codegen is
 * delegated to {@link LALDefCodegen}.
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

    /**
     * Generates the {@code _extractor()} private method for the LAL
     * {@code extractor { ... }} block.
     *
     * <p>LAL example:
     * <pre>{@code
     * extractor {
     *   service parsed.service as String
     *   tag 'status.code': parsed?.response?.responseCode?.value
     *   metrics { name "log_count"; value 1 }
     * }
     * }</pre>
     *
     * <p>The generated method signature is:
     * {@code private void _extractor(MetricExtractor _e, LalRuntimeHelper h)}.
     * The call site in {@code execute()} guards it with
     * {@code if (!ctx.shouldAbort())}.
     */
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
                    .append(elTypeName).append(") h.ctx().input();\n");
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

    /**
     * Walks extractor statements and dispatches each to its codegen method:
     * field assignments, tag assignments, if-blocks, metrics blocks,
     * output-field assignments, and def statements.
     */
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
                LALDefCodegen.generateDefStatement(
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

    /**
     * Generates a standard field assignment to the output builder.
     *
     * <p>LAL: {@code service parsed.service as String}
     * <br>Generated: {@code _o.setServiceName(h.toStr(h.mapVal("service")))}
     *
     * <p>Standard fields: service, instance, endpoint, layer, traceId,
     * segmentId, spanId, timestamp.
     */
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
            LALValueCodegen.generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(", \"")
              .append(LALCodegenHelper.escapeJava(field.getFormatPattern()))
              .append("\")");
        } else if (paramType.isEnum()) {
            sb.append(paramType.getName()).append(".valueOf(");
            LALValueCodegen.generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(")");
        } else {
            LALValueCodegen.generateCastedValueAccess(sb, field.getValue(), effectiveCast, genCtx);
        }
        sb.append(");\n");
    }

    static void generateIfBlockInExtractor(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
        sb.append("  if (");
        LALValueCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
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

    /**
     * Generates an inlined {@code metrics { ... }} block inside the extractor.
     *
     * <p>LAL example:
     * <pre>{@code
     * metrics {
     *   name "log_count"
     *   timestamp log.timestamp as Long
     *   labels level: parsed.level, service: parsed.service
     *   value 1
     * }
     * }</pre>
     *
     * <p>Generated code prepares a {@code SampleBuilder} via
     * {@code _e.prepareMetrics()}, sets name/timestamp/labels/value, then
     * calls {@code _e.submitMetrics()}.
     */
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
            LALValueCodegen.generateCastedValueAccess(sb, block.getTimestampValue(),
                block.getTimestampCast(), genCtx);
            sb.append(");\n");
        }
        if (!block.getLabels().isEmpty()) {
            sb.append("  { java.util.Map _labels = new java.util.LinkedHashMap();\n");
            for (final Map.Entry<String, LALScriptModel.TagValue> entry
                    : block.getLabels().entrySet()) {
                sb.append("    _labels.put(\"")
                    .append(LALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
                LALValueCodegen.generateCastedValueAccess(sb, entry.getValue().getValue(),
                    entry.getValue().getCastType(), genCtx);
                sb.append(");\n");
            }
            sb.append("    _metrics.labels(_labels); }\n");
        }
        if (block.getValue() != null) {
            sb.append("  _metrics.value(");
            if ("Long".equals(block.getValueCast())) {
                sb.append("(double) h.toLong(");
                LALValueCodegen.generateValueAccess(sb, block.getValue(), genCtx);
                sb.append(")");
            } else if ("Integer".equals(block.getValueCast())) {
                sb.append("(double) h.toInt(");
                LALValueCodegen.generateValueAccess(sb, block.getValue(), genCtx);
                sb.append(")");
            } else {
                if (block.getValue().isNumberLiteral()) {
                    sb.append("(double) ").append(block.getValue().getSegments().get(0));
                } else {
                    sb.append("((Number) ");
                    LALValueCodegen.generateValueAccess(sb, block.getValue(), genCtx);
                    sb.append(").doubleValue()");
                }
            }
            sb.append(");\n");
        }
        sb.append("  _e.submitMetrics(h.ctx(), _metrics);\n");
        sb.append("  } }\n");
    }

    // ==================== Tag assignment ====================

    /**
     * Generates tag assignment statements in the extractor.
     *
     * <p>LAL: {@code tag 'status.code': parsed?.response?.responseCode?.value}
     * <br>Generated: {@code _o.addTag("status.code", h.toStr(...))}
     */
    static void generateTagAssignment(final StringBuilder sb,
                                       final LALScriptModel.TagAssignment tag,
                                       final LALClassGenerator.GenCtx genCtx) {
        for (final Map.Entry<String, LALScriptModel.TagValue> entry
                : tag.getTags().entrySet()) {
            sb.append("  _o.addTag(\"")
              .append(LALCodegenHelper.escapeJava(entry.getKey())).append("\", ");
            LALValueCodegen.generateStringValueAccess(sb, entry.getValue().getValue(),
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

    /**
     * Generates an output-field assignment for custom output types.
     *
     * <p>LAL: {@code latency parsed.latency as Long}
     * (where {@code latency} is not a standard field but a field on the
     * {@code outputType} class, e.g. {@code SampledTrace.setLatency(long)})
     * <br>Generated: {@code _o.setLatency(h.toLong(h.mapVal("latency")))}
     *
     * <p>The setter is validated at compile time via reflection on the
     * output type. If no matching setter exists, compilation fails.
     */
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
            LALValueCodegen.generateCastedValueAccess(sb, field.getValue(), "String", genCtx);
            sb.append(").toUpperCase())");
        } else {
            LALValueCodegen.generateCastedValueAccess(sb, field.getValue(), effectiveCast, genCtx);
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

    /**
     * Generates the {@code _sink()} private method for the LAL
     * {@code sink { ... }} block.
     *
     * <p>LAL example:
     * <pre>{@code
     * sink {
     *   sampler {
     *     rateLimit("${log.service}:${parsed?.response?.responseCode}") {
     *       rpm 6000
     *     }
     *   }
     * }
     * }</pre>
     *
     * <p>The generated method signature is:
     * {@code private void _sink(FilterSpec _f, LalRuntimeHelper h)}.
     * Sink statements include {@code enforcer()}, {@code dropper()},
     * {@code sampler { ... }}, and if-blocks.
     */
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
                    .append(elTypeName).append(") h.ctx().input();\n");
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
        LALValueCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
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

    /**
     * Generates a {@code sampler { ... }} block inside the sink.
     */
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
        LALValueCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
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

    /**
     * Generates a {@code rateLimit("id") { rpm N }} block.
     *
     * <p>The ID can be a plain string or an interpolated string:
     * <pre>{@code
     * rateLimit("${log.service}:${parsed?.response?.responseCode}") {
     *   rpm 6000
     * }
     * }</pre>
     *
     * <p>Generated: {@code _f.sampler().rateLimit(h.ctx(), "" + ... , 6000)}
     */
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
                    LALValueCodegen.generateValueAccess(sb, part.getExpression(), genCtx);
                    sb.append(")");
                }
            }
        } else {
            sb.append("\"").append(LALCodegenHelper.escapeJava(block.getId())).append("\"");
        }
        sb.append(", ").append(block.getRpm()).append(");\n");
    }
}
