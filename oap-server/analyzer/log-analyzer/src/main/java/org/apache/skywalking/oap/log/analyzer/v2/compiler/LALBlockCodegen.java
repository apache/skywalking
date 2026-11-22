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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import java.lang.reflect.Method;

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

    /** The runtime helper that sets a standard field on the rule's metadata too, as Groovy LAL set the log. */
    private static final Map<LALScriptModel.FieldType, String> LOG_FIELD_SETTERS = Map.of(
        LALScriptModel.FieldType.SERVICE, "setLogService",
        LALScriptModel.FieldType.INSTANCE, "setLogServiceInstance",
        LALScriptModel.FieldType.ENDPOINT, "setLogEndpoint",
        LALScriptModel.FieldType.TRACE_ID, "setLogTraceId",
        LALScriptModel.FieldType.SEGMENT_ID, "setLogSegmentId",
        LALScriptModel.FieldType.SPAN_ID, "setLogSpanId");

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
        final Map<String, LALClassGenerator.LocalVarInfo> outerDefs = new TreeMap<>(genCtx.localVars);
        final Object[] savedState = genCtx.saveProtoVarState();
        genCtx.resetProtoVars();
        takeOuterDefs(outerDefs, genCtx);

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
            .append(METRIC_EXTRACTOR).append(" _e, ").append(H).append(" h")
            .append(outerDefParameters(outerDefs)).append(") {\n");

        final List<String[]> lvtVars = new ArrayList<>();
        lvtVars.add(new String[]{"_e", "L" + METRIC_EXTRACTOR.replace('.', '/') + ";"});
        lvtVars.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});
        addOuterDefLvt(outerDefs, lvtVars);

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
        body.append(genCtx.tempVarDecls);
        lvtVars.addAll(genCtx.tempLvtVars);

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
        sb.append("    ").append(methodName).append("(filterSpec.extractor(), h")
          .append(outerDefArguments(outerDefs)).append(");\n");
        sb.append("  }\n");
    }

    /**
     * The defs in scope where an extractor or sink sits become its method's parameters: a Groovy closure saw the
     * locals around it.
     */
    private static void takeOuterDefs(final Map<String, LALClassGenerator.LocalVarInfo> outerDefs,
                                      final LALClassGenerator.GenCtx genCtx) {
        for (final Map.Entry<String, LALClassGenerator.LocalVarInfo> def : outerDefs.entrySet()) {
            genCtx.localVars.put(def.getKey(), def.getValue());
            genCtx.declaredDefVars.put(def.getValue().javaVarName, def.getValue().resolvedType);
        }
    }

    private static String outerDefParameters(final Map<String, LALClassGenerator.LocalVarInfo> outerDefs) {
        final StringBuilder params = new StringBuilder();
        for (final LALClassGenerator.LocalVarInfo def : outerDefs.values()) {
            params.append(", ").append(LALClassGenerator.sourceNameOf(def.resolvedType)).append(" ").append(def.javaVarName);
        }
        return params.toString();
    }

    private static String outerDefArguments(final Map<String, LALClassGenerator.LocalVarInfo> outerDefs) {
        final StringBuilder args = new StringBuilder();
        for (final LALClassGenerator.LocalVarInfo def : outerDefs.values()) {
            args.append(", ").append(def.javaVarName);
        }
        return args.toString();
    }

    private static void addOuterDefLvt(final Map<String, LALClassGenerator.LocalVarInfo> outerDefs,
                                       final List<String[]> lvtVars) {
        for (final LALClassGenerator.LocalVarInfo def : outerDefs.values()) {
            lvtVars.add(new String[]{def.javaVarName, def.descriptor});
        }
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
            } else if (stmt instanceof LALScriptModel.AbortStatement) {
                // The rest of the extractor and the sink do not run once aborted.
                sb.append("  h.ctx().abort();\n");
                sb.append("  return;\n");
                continue;
            } else {
                throw LALCodegenHelper.notAllowedIn(stmt, "an extractor", null);
            }
            // Statement-level captureLine — opt-in on the recorder side via
            // session granularity. The verbatim source text is paired with the
            // line number so the UI can render "line N: <fragment>" without a
            // round-trip to the source file. Block-mode recorders skip; the
            // codegen-side helper is also a no-op when injection is off.
            LALCodegenHelper.emitCaptureLine(
                sb, genCtx.ruleName, stmt.getSourceLine(), stmt.getSourceText());
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
        Method setter = null;
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
        if (field.getFieldType() == LALScriptModel.FieldType.TIMESTAMP) {
            generateTimestampToOutput(sb, field, setter, genCtx);
            return;
        }
        // The value is read once, converted by the rule's cast, then by the setter's parameter type. As Groovy
        // LAL's statements, a null leaves the field, the output's and the rule's metadata's, as it was.
        final boolean layer = field.getFieldType() == LALScriptModel.FieldType.LAYER;
        final String mirror = LOG_FIELD_SETTERS.get(field.getFieldType());
        sb.append("  { java.lang.Object _v = ");
        LALValueCodegen.generateBoxedValue(sb, field.getValue(), field.getCastType(), genCtx);
        sb.append(";\n");
        sb.append("    if (_v != null) { _o.").append(setter.getName()).append("(");
        if (mirror != null && paramType == String.class) {
            sb.append("h.").append(mirror).append("(h.toStr(_v))");
        } else {
            sb.append(parameterValue(setter, "_v", false));
        }
        sb.append(");");
        if (mirror != null && paramType != String.class) {
            sb.append(" h.").append(mirror).append("(h.toStr(_v));");
        }
        sb.append(" }\n");
        if (layer) {
            // ExecutionContext.layer() records the rule's decision on its context and its metadata copy, which
            // FilterSpec.doSink() (the layer: auto check) and the sink listeners read; a null drops a saved log.
            sb.append("    h.ctx().layer(h.toStr(_v));\n");
        }
        sb.append("  }\n");
    }

    /**
     * A setter's argument from an object holding the value as the rule cast it.
     *
     * @param upperCaseEnum whether an enum constant is named case-insensitively, as an output field's is
     */
    private static String parameterValue(final Method setter, final String value, final boolean upperCaseEnum) {
        final Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType == String.class) {
            return "h.toStr(" + value + ")";
        }
        if (paramType.isEnum() || paramType == Layer.class) {
            // `Layer` was historically an enum and is now a registry-backed value type with a matching
            // `valueOf(String)`, so `layer "MYSQL"` resolves to the typed setter argument either way.
            return paramType.getName() + ".valueOf(" + (upperCaseEnum
                ? "((String) h.toStr(" + value + ")).toUpperCase()" : "h.toStr(" + value + ")") + ")";
        }
        if (paramType.isPrimitive()) {
            final String helper = LALValueCodegen.conversionHelper(paramType);
            if (helper == null) {
                throw new IllegalArgumentException(
                    setter.getName() + "(" + paramType.getName() + "): a setter of a char, short or byte is not "
                        + "supported");
            }
            return helper + "(" + value + ")";
        }
        // A wrapper of a primitive the runtime helper converts to keeps a null (h.asInteger, ...).
        final Class<?> primitive = paramType == Boolean.class ? boolean.class : DslMethods.unboxed(paramType);
        if (primitive != null && LALValueCodegen.conversionHelper(primitive) != null) {
            return "h.as" + paramType.getSimpleName() + "(" + value + ")";
        }
        return "((" + paramType.getName() + ") " + value + ")";
    }

    static void generateIfBlockInExtractor(
            final StringBuilder sb,
            final LALScriptModel.IfBlock ifBlock,
            final LALClassGenerator.GenCtx genCtx) {
        sb.append("  if (");
        LALValueCodegen.generateCondition(sb, ifBlock.getCondition(), genCtx);
        sb.append(") {\n");
        genCtx.inBranchScope(() -> generateExtractorBody(sb, ifBlock.getThenBranch(), genCtx));
        sb.append("  }\n");
        if (!ifBlock.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            genCtx.inBranchScope(() -> generateExtractorBody(sb, ifBlock.getElseBranch(), genCtx));
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
        // In source order, as Groovy LAL ran them: a statement reading a value another consumes sees its turn.
        for (final LALScriptModel.MetricsStatement statement : block.getStatements()) {
            switch (statement.getKind()) {
                case NAME:
                    sb.append("  _metrics.name(");
                    LALValueCodegen.generateStringValueAccess(sb, statement.getValue(), statement.getCast(), genCtx);
                    sb.append(");\n");
                    break;
                case TIMESTAMP:
                    final String helper = numericHelper(statement.getCast(), "timestamp", "h.toLong");
                    sb.append("  _metrics.timestamp(").append("h.toLong".equals(helper) ? "" : "(long) ")
                      .append(helper).append("(");
                    LALValueCodegen.generateValueAccess(sb, statement.getValue(), genCtx);
                    if (statement.getCast() != null) {
                        LALValueCodegen.rejectUnconvertibleCast(statement.getValue(), statement.getCast(), genCtx);
                    }
                    sb.append("));\n");
                    break;
                case LABELS:
                    sb.append("  { java.util.Map _labels = new java.util.LinkedHashMap();\n");
                    for (final Map.Entry<String, LALScriptModel.TagValue> entry : statement.getLabelEntries()) {
                        sb.append("    _labels.put(\"")
                            .append(DslJavaSourceText.toLiteral(entry.getKey())).append("\", ");
                        LALValueCodegen.generateBoxedValue(sb, entry.getValue().getValue(),
                            entry.getValue().getCastType(), genCtx);
                        sb.append(");\n");
                    }
                    sb.append("    _metrics.labels(_labels); }\n");
                    break;
                default:
                    generateMetricValue(sb, statement, genCtx);
                    break;
            }
        }
        sb.append("  _e.submitMetrics(h.ctx(), _metrics);\n");
        sb.append("  } }\n");
    }

    private static void generateMetricValue(final StringBuilder sb,
                                            final LALScriptModel.MetricsStatement statement,
                                            final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess value = statement.getValue();
        sb.append("  _metrics.value(");
        if (statement.getCast() != null) {
            final String helper = numericHelper(statement.getCast(), "value", null);
            sb.append("h.toDouble".equals(helper) ? "" : "(double) ").append(helper).append("(");
            LALValueCodegen.generateValueAccess(sb, value, genCtx);
            LALValueCodegen.rejectUnconvertibleCast(value, statement.getCast(), genCtx);
            sb.append(")");
        } else if (value.isNumberLiteral() && value.getChain().isEmpty()) {
            sb.append("(double) ").append(LALLiterals.javaText(value.getSegments().get(0)));
        } else {
            sb.append("((Number) ");
            LALValueCodegen.generateValueAccess(sb, value, genCtx);
            sb.append(").doubleValue()");
        }
        sb.append(");\n");
    }

    /**
     * The runtime helper converting a metric's timestamp or value by its cast: they are numbers, so a non-numeric
     * cast would not convert.
     */
    private static String numericHelper(final String cast, final String statement, final String uncast) {
        if (cast == null) {
            return uncast;
        }
        final String helper = LALValueCodegen.numericConversionHelper(cast);
        if (helper == null) {
            throw new IllegalArgumentException(
                "A metrics " + statement + " is a number: 'as " + cast + "' is not supported there, "
                    + "use as Long, Integer, Double or Float");
        }
        return helper;
    }

    // ==================== Tag assignment ====================

    /**
     * Generates tag assignment statements in the extractor.
     *
     * <p>LAL: {@code tag 'status.code': parsed?.response?.responseCode?.value}
     * <br>Generated: {@code _o.addTag("status.code", h.tag("status.code", ...))}
     */
    static void generateTagAssignment(final StringBuilder sb,
                                       final LALScriptModel.TagAssignment tag,
                                       final LALClassGenerator.GenCtx genCtx) {
        // Groovy LAL evaluated the whole map, then added its tags: a value reading tag() sees the tags before.
        sb.append("  {\n");
        final Map<String, String> valueOfKey = new LinkedHashMap<>();
        int n = 0;
        for (final Map.Entry<String, LALScriptModel.TagValue> entry : tag.getEntries()) {
            final String var = "_tv" + n++;
            sb.append("    java.lang.Object ").append(var).append(" = ");
            LALValueCodegen.generateBoxedValue(sb, entry.getValue().getValue(), entry.getValue().getCastType(),
                genCtx);
            sb.append(";\n");
            valueOfKey.put(entry.getKey(), var);
        }
        for (final Map.Entry<String, String> entry : valueOfKey.entrySet()) {
            final String key = DslJavaSourceText.toLiteral(entry.getKey());
            sb.append("    _o.addTag(\"").append(key).append("\", h.tag(\"").append(key).append("\", ")
              .append(entry.getValue()).append("));\n");
        }
        sb.append("  }\n");
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
        final Method setter = findSetter(genCtx.outputType, setterName);
        if (setter == null) {
            throw new IllegalArgumentException(
                "Output type " + genCtx.outputType.getName()
                + " has no setter " + setterName
                + "() for output field '" + fieldName + "'");
        }

        // The value as the rule cast it, then converted to the parameter. A parameter that cannot hold a null
        // (a primitive, an enum) leaves its field unset for one, as Groovy LAL's statements did.
        final Class<?> paramType = setter.getParameterTypes()[0];
        final StringBuilder value = new StringBuilder();
        LALValueCodegen.generateBoxedValue(value, field.getValue(), field.getCastType(), genCtx);
        if ((paramType.isPrimitive() || paramType.isEnum() || paramType == Layer.class) && !genCtx.lastNonNull) {
            sb.append("  { java.lang.Object _v = ").append(value).append(";\n");
            sb.append("    if (_v != null) { _o.").append(setterName).append("(")
              .append(parameterValue(setter, "_v", true)).append("); } }\n");
        } else {
            final boolean string = paramType == String.class && "String".equals(field.getCastType());
            sb.append("  _o.").append(setterName).append("(")
              .append(string ? value.toString() : parameterValue(setter, value.toString(), true)).append(");\n");
        }
    }

    /**
     * {@code timestamp}, as Groovy LAL's {@code timestamp(String[, pattern])}: an empty value, one that is not a
     * number (without a pattern), or one the pattern does not parse leaves the timestamp as it was.
     */
    private static void generateTimestampToOutput(final StringBuilder sb,
                                                  final LALScriptModel.FieldAssignment field,
                                                  final Method setter,
                                                  final LALClassGenerator.GenCtx genCtx) {
        final Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType != long.class && paramType != Long.class) {
            throw new IllegalArgumentException(
                setter.getDeclaringClass().getName() + "." + setter.getName() + " takes a " + paramType.getName()
                    + ", not a long, so it cannot be the timestamp statement's setter");
        }
        sb.append("  { java.lang.Long _ts = h.timestampOf(");
        LALValueCodegen.generateBoxedValue(sb, field.getValue(), field.getCastType(), genCtx);
        sb.append(", ");
        if (field.getFormatPattern() == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(DslJavaSourceText.toLiteral(field.getFormatPattern())).append("\"");
        }
        sb.append(");\n");
        // Groovy LAL set the log's own timestamp, which a later `log.timestamp` read (a metric's, say); the rule's
        // metadata copy is that log here.
        sb.append("    if (_ts != null) { _o.").append(setter.getName()).append("(_ts")
          .append(paramType == long.class ? ".longValue()" : "").append(");\n")
          .append("      h.ctx().metadata().setTimestamp(_ts.longValue()); } }\n");
    }

    private static Method findSetter(
            final Class<?> clazz, final String setterName) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (final Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(setterName)
                        && m.getParameterCount() == 1) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
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
        final Map<String, LALClassGenerator.LocalVarInfo> outerDefs = new TreeMap<>(genCtx.localVars);
        final Object[] savedState = genCtx.saveProtoVarState();
        genCtx.resetProtoVars();
        takeOuterDefs(outerDefs, genCtx);

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
            .append(FILTER_SPEC).append(" _f, ").append(H).append(" h")
            .append(outerDefParameters(outerDefs)).append(") {\n");

        final List<String[]> lvtVars = new ArrayList<>();
        lvtVars.add(new String[]{"_f", "L" + FILTER_SPEC.replace('.', '/') + ";"});
        lvtVars.add(new String[]{"h", "L" + H.replace('.', '/') + ";"});
        addOuterDefLvt(outerDefs, lvtVars);

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
        body.append(genCtx.tempVarDecls);
        lvtVars.addAll(genCtx.tempLvtVars);

        body.append(bodyContent);
        body.append("}\n");
        genCtx.privateMethods.add(new LALClassGenerator.PrivateMethod(
            body.toString(), lvtVars.toArray(new String[0][])));

        genCtx.restoreProtoVarState(savedState);

        sb.append("  if (!ctx.shouldAbort()) {\n");
        sb.append("    ").append(methodName).append("(filterSpec, h")
          .append(outerDefArguments(outerDefs)).append(");\n");
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
            } else if (stmt instanceof LALScriptModel.AbortStatement) {
                // Groovy LAL still saved a log aborted here; use dropper {} not to save it.
                throw LALCodegenHelper.notAllowedIn(stmt, "a sink", "use dropper {} not to save the log");
            } else if (stmt instanceof LALScriptModel.RateLimitBlock) {
                throw LALCodegenHelper.notAllowedIn(stmt, "a sink", "put it in a sampler {}");
            } else {
                throw LALCodegenHelper.notAllowedIn(stmt, "a sink", null);
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
            } else {
                throw LALCodegenHelper.notAllowedIn(content, "a sampler", null);
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
            if (stmt instanceof LALScriptModel.RateLimitBlock) {
                generateRateLimitInline(sb, (LALScriptModel.RateLimitBlock) stmt, genCtx);
            } else if (stmt instanceof LALScriptModel.IfBlock) {
                generateSamplerIfBlock(sb, (LALScriptModel.IfBlock) stmt, genCtx);
            } else {
                throw LALCodegenHelper.notAllowedIn(stmt, "a sampler", null);
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
                    sb.append("\"").append(DslJavaSourceText.toLiteral(part.getLiteral()))
                      .append("\"");
                } else {
                    sb.append("String.valueOf(");
                    LALValueCodegen.generateBoxedValue(sb, part.getExpression(), part.getCastType(), genCtx);
                    sb.append(")");
                }
            }
        } else {
            sb.append("\"").append(DslJavaSourceText.toLiteral(block.getId())).append("\"");
        }
        sb.append(", ").append(block.getRpm()).append(");\n");
    }
}
