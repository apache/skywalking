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
import org.apache.skywalking.oap.server.core.source.LogMetadata;

/**
 * Code generation for LAL expression evaluation: value access, conditions,
 * type casts, and all data-source-specific access patterns.
 *
 * <p>This class handles the expression-level codegen that
 * {@link LALBlockCodegen} delegates to when it encounters value expressions
 * inside block statements. The central dispatch method is
 * {@link #generateValueAccess}, which routes each expression to the
 * appropriate codegen path:
 *
 * <pre>{@code
 * Expression type          LAL example                        Generated code
 * ─────────────────────── ─────────────────────────────────── ────────────────────────────
 * tag() function           tag("LOG_KIND")                    h.tagValue("LOG_KIND")
 * String literal           "SLOW_SQL"                         "SLOW_SQL"
 * Number literal           500                                Integer.valueOf(500)
 * ProcessRegistry          ProcessRegistry.generateVirtual..  ProcessRegistry.generate...
 * log.* field              log.traceContext.traceId            h.ctx().metadata().get...
 * parsed.* (json/yaml)     parsed.service                     h.mapVal("service")
 * parsed.* (text)          parsed.level                       h.group("level")
 * parsed.* (typed proto)   parsed?.response?.responseCode     ((Type)_p).getResponse()...
 * def variable             myVar?.getAsString()               _def_myVar?.getAsString()
 * Parenthesized            (expr as String).trim()            h.toStr(...).trim()
 * String concat            "${log.service}:${parsed.code}"    "" + ... + ":" + ...
 * }</pre>
 *
 * <p>Condition codegen ({@link #generateCondition}) handles {@code if}
 * conditions: comparisons ({@code ==}, {@code !=}, {@code <}, {@code >}),
 * logical operators ({@code &&}, {@code ||}), negation ({@code !}), and
 * expression-as-boolean ({@code if (parsed?.flags?.toString())}).
 */
final class LALValueCodegen {

    static final String LOGDATA_BUILDER_CAST =
        "((org.apache.skywalking.apm.network.logging.v3.LogData.Builder) h.ctx().input())";
    static final String PROCESS_REGISTRY =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry";

    private LALValueCodegen() {
        // utility class
    }

    // ==================== Conditions ====================

    /**
     * Generates code for an {@code if} condition expression.
     *
     * <p>LAL examples and their generated code:
     * <pre>{@code
     * // Comparison (==, !=):
     * if (tag("LOG_KIND") == "SLOW_SQL")
     * → java.util.Objects.equals(h.tagValue("LOG_KIND"), "SLOW_SQL")
     *
     * // Numeric comparison (<, >, <=, >=):
     * if (parsed?.response?.responseCode?.value as Integer < 400)
     * → ((Type)_p).getResponse().getResponseCode().getValue() < 400L
     *
     * // Logical operators:
     * if (condA && condB)  →  (condA && condB)
     * if (!cond)           →  !(cond)
     *
     * // Expression-as-boolean (truthy check):
     * if (parsed?.commonProperties?.responseFlags?.toString())
     * → h.isNotEmpty(h.toString(...))
     * }</pre>
     */
    static void generateCondition(final StringBuilder sb,
                                   final LALScriptModel.Condition cond,
                                   final LALClassGenerator.GenCtx genCtx) {
        if (cond instanceof LALScriptModel.ComparisonCondition) {
            final LALScriptModel.ComparisonCondition cc =
                (LALScriptModel.ComparisonCondition) cond;
            switch (cc.getOp()) {
                case EQ:
                    if (cc.getRight() instanceof LALScriptModel.NumberConditionValue) {
                        generateNumericComparison(sb, cc, " == ", genCtx);
                    } else {
                        sb.append("java.util.Objects.equals(");
                        generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
                        sb.append(", ");
                        generateConditionValue(sb, cc.getRight(), genCtx);
                        sb.append(")");
                    }
                    break;
                case NEQ:
                    if (cc.getRight() instanceof LALScriptModel.NumberConditionValue) {
                        generateNumericComparison(sb, cc, " != ", genCtx);
                    } else {
                        sb.append("!java.util.Objects.equals(");
                        generateValueAccessObj(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
                        sb.append(", ");
                        generateConditionValue(sb, cc.getRight(), genCtx);
                        sb.append(")");
                    }
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

    /**
     * Generates a value access with an explicit type cast.
     *
     * <p>LAL: {@code parsed.latency as Long}
     * <br>Generated: {@code h.toLong(h.mapVal("latency"))}
     *
     * <p>Supported cast types: String, Long, Integer, Boolean.
     */
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

    /**
     * Generates a value access that must produce a String result (used by
     * tag assignments). Wraps the inner expression with {@code h.toStr()}.
     */
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

    /**
     * Generates a value access that produces an Object (boxed) result.
     * Only applies String cast; other casts pass through as-is.
     */
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

    /**
     * Central dispatch for all LAL value-access expressions. Routes each
     * expression to the appropriate codegen path based on its AST type.
     *
     * <p>Dispatch order:
     * <ol>
     *   <li>String concatenation ({@code "..." + expr + "..."})</li>
     *   <li>Parenthesized expression ({@code (expr as Type).chain})</li>
     *   <li>Standalone {@code tag("KEY")} function call</li>
     *   <li>String/number literals</li>
     *   <li>{@code ProcessRegistry.method(...)} static calls</li>
     *   <li>{@code log.X.Y} direct proto getter chains</li>
     *   <li>{@code parsed.X.Y} with compile-time type analysis</li>
     *   <li>{@code def} variable reference with method chain</li>
     *   <li>Fallback: compile-time error</li>
     * </ol>
     *
     * <p>Note: standalone {@code tag("KEY")} and {@code parsed.tag("KEY")}
     * are different AST structures. {@code tag("KEY")} is a top-level
     * function call handled here; {@code parsed.tag("KEY")} is a
     * {@code parsed}-prefixed chain where {@code tag("KEY")} appears as a
     * MethodSegment, handled by {@link #generateParsedAccess}.
     */
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

        // tag("KEY") — reads LogData tags via LalRuntimeHelper.tagValue().
        // Only valid when input is LogData.Builder (i.e. no inputType, or inputType
        // is LogData.Builder).  For typed inputs, tag() is not supported — use
        // parsed.* to access fields on the typed input.
        if ("tag".equals(value.getFunctionCallName())) {
            if (value.getFunctionCallArgs().size() != 1
                    || !value.getFunctionCallArgs().get(0).getValue().isStringLiteral()) {
                throw new IllegalArgumentException(
                    "tag() requires exactly one string literal argument, "
                        + "e.g. tag(\"KEY\")");
            }
            if (genCtx.inputType != null
                    && !LALCodegenHelper.LOGDATA_BUILDER_CLASS
                        .isAssignableFrom(genCtx.inputType)) {
                throw new IllegalArgumentException(
                    "tag() reads LogData tags but the input type is "
                        + genCtx.inputType.getName()
                        + ". Use a json{}/yaml{}/text{} parser, or access "
                        + "typed fields via parsed.* instead.");
            }
            sb.append("h.tagValue(\"");
            final String key = value.getFunctionCallArgs().get(0)
                .getValue().getSegments().get(0);
            sb.append(LALCodegenHelper.escapeJava(key)).append("\")");
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
                LALDefCodegen.generateDefVarChain(sb, localVar, chain, genCtx);
                return;
            }
        }

        // No matching codegen path — fail at compile time instead of silently
        // generating null.
        if (chain.isEmpty()) {
            final String desc = value.getFunctionCallName() != null
                ? "function call '" + value.getFunctionCallName() + "(...)'"
                : "expression '" + value.getSegments() + "'";
            throw new IllegalArgumentException(
                "Cannot resolve " + desc + " — no matching codegen path. "
                    + "Parser type: " + genCtx.parserType
                    + (genCtx.inputType != null
                        ? ", inputType: " + genCtx.inputType.getName() : ""));
        }
        // Treat as parsed ref
        generateParsedAccess(sb, chain, genCtx);
    }

    // ==================== Parenthesized expression ====================

    /**
     * Generates code for a parenthesized expression with optional type cast
     * and method chain.
     *
     * <p>LAL: {@code (parsed.component as String).trim()}
     * <br>Generated: {@code h.toStr(h.mapVal("component")).trim()}
     */
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

    /**
     * Generate code for {@code log.xxx} field access in LAL scripts.
     * <ul>
     *   <li>Metadata fields (service, endpoint, layer, timestamp, traceContext)
     *       &rarr; {@code h.ctx().metadata().getXxx()}</li>
     *   <li>LogData-only fields (body, tags)
     *       &rarr; {@code ((LogData.Builder) h.ctx().input()).getXxx()}</li>
     * </ul>
     */
    static void generateLogAccess(final StringBuilder sb,
                                   final List<LALScriptModel.ValueAccessSegment> chain) {
        if (chain.isEmpty()) {
            sb.append("h.ctx().input()");
            return;
        }

        String current;
        boolean needsBoxing = false;
        String boxType = null;

        // Determine root based on first field
        final LALScriptModel.ValueAccessSegment first = chain.get(0);
        if (!(first instanceof LALScriptModel.FieldSegment)) {
            current = LOGDATA_BUILDER_CAST;
        } else {
            final String firstName = ((LALScriptModel.FieldSegment) first).getName();
            if (LALCodegenHelper.METADATA_GETTERS.containsKey(firstName)) {
                current = "h.ctx().metadata()";
            } else if (LALCodegenHelper.LOG_GETTERS.containsKey(firstName)) {
                current = LOGDATA_BUILDER_CAST;
            } else {
                throw new IllegalArgumentException(
                    "Unknown log field: log." + firstName
                        + ". Supported metadata fields: "
                        + LALCodegenHelper.METADATA_GETTERS.keySet()
                        + ", LogData fields: "
                        + LALCodegenHelper.LOG_GETTERS.keySet());
            }
        }

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            if (seg instanceof LALScriptModel.FieldSegment) {
                final String name = ((LALScriptModel.FieldSegment) seg).getName();
                if (i == 0 && LALCodegenHelper.METADATA_GETTERS.containsKey(name)) {
                    if ("traceContext".equals(name)) {
                        current = current + ".getTraceContext()";
                    } else {
                        current = current + "."
                            + LALCodegenHelper.METADATA_GETTERS.get(name) + "()";
                        if (LALCodegenHelper.LONG_FIELDS.contains(name)) {
                            needsBoxing = true;
                            boxType = "Long";
                        }
                    }
                } else if (i == 0 && LALCodegenHelper.LOG_GETTERS.containsKey(name)) {
                    current = current + "."
                        + LALCodegenHelper.LOG_GETTERS.get(name) + "()";
                } else if (i == 1 && current.endsWith(".getTraceContext()")
                        && LALCodegenHelper.METADATA_TRACE_GETTERS.containsKey(name)) {
                    current = current + "."
                        + LALCodegenHelper.METADATA_TRACE_GETTERS.get(name) + "()";
                    if (LALCodegenHelper.INT_FIELDS.contains(name)) {
                        needsBoxing = true;
                        boxType = "Integer";
                    }
                } else {
                    throw new IllegalArgumentException(
                        "Unknown log field: log." + name
                            + ". Supported metadata fields: "
                            + LALCodegenHelper.METADATA_GETTERS.keySet()
                            + ", traceContext."
                            + LALCodegenHelper.METADATA_TRACE_GETTERS.keySet()
                            + ", LogData fields: "
                            + LALCodegenHelper.LOG_GETTERS.keySet());
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

    /**
     * Generates code for {@code parsed.X.Y} field access, with different
     * strategies depending on the parser type:
     *
     * <pre>{@code
     * Parser     LAL                             Generated
     * ────────── ──────────────────────────────── ──────────────────────────
     * json/yaml  parsed.service                   h.mapVal("service")
     * json/yaml  parsed.client_process.address    h.mapVal("client_process", "address")
     * text       parsed.level                     h.group("level")
     * none+type  parsed?.response?.responseCode   ((Type)_p).getResponse()...
     * none       parsed.service                   h.ctx().metadata().getService()
     * }</pre>
     *
     * <p>For typed proto access ({@code NONE} parser with {@code inputType}),
     * uses compile-time reflection to generate direct getter chains with
     * null-safe navigation and local variable caching.
     */
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
                    // No parser and no inputType — fall back to LogMetadata
                    current = generateExtraLogAccess(fieldSegments, LogMetadata.class,
                        "h.ctx().metadata()", false, genCtx);
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

    /**
     * Generates a reflection-based getter chain on a typed root object.
     * Used for both typed proto access ({@code parsed?.response?.responseCode})
     * and LogMetadata fallback ({@code parsed.service} without a parser).
     *
     * <p>Each getter call is validated at compile time via reflection. Safe
     * navigation ({@code ?.}) generates null checks. Intermediate results
     * are cached in local variables ({@code _t0}, {@code _t1}, ...) to avoid
     * redundant getter calls when the same chain prefix appears multiple times.
     */
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
            String getterName = "get" + Character.toUpperCase(field.charAt(0))
                + field.substring(1);

            // Apply getter aliases (e.g., traceSegmentId → segmentId on LogMetadata)
            final String alias = LALCodegenHelper.METADATA_GETTER_ALIASES.get(getterName);
            if (alias != null) {
                try {
                    currentType.getMethod(getterName);
                } catch (NoSuchMethodException ignored) {
                    getterName = alias;
                }
            }

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

    // ==================== ProcessRegistry ====================

    /**
     * Generates a {@code ProcessRegistry} static method call.
     *
     * <p>LAL: {@code ProcessRegistry.generateVirtualRemoteProcess(
     *     parsed.service as String, parsed.serviceInstance as String,
     *     parsed.client_process.address as String)}
     * <br>Generated: {@code ProcessRegistry.generateVirtualRemoteProcess(
     *     h.toStr(...), h.toStr(...), h.toStr(...))}
     */
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

    /**
     * Appends a method call segment to the current expression chain.
     * Handles safe-navigation ({@code ?.method()}) by wrapping in a null
     * guard. Special-cases {@code ?.toString()} and {@code ?.trim()} to
     * use helper methods for null-safe behavior.
     */
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

    /**
     * Generates the argument list for a method call. Handles string literals,
     * number literals, boolean/null literals, and def variable references.
     */
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
                    throw new IllegalArgumentException(
                        "Unknown identifier used as method argument: '" + text + "'");
                }
            } else {
                sb.append("null");
            }
        }
        return sb.toString();
    }
}
