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
 * Arithmetic (int)          (tag("a") as Integer) + (tag("b") as Integer)  Integer.valueOf(h.toInt(...) + h.toInt(...))
 * Arithmetic (mixed)        (tag("a") as Integer) * (tag("b") as Long)     Long.valueOf((long) h.toInt(...) * h.toLong(...))
 * Arithmetic (double)       (parsed.x as Double) - 1                       Double.valueOf(h.toDouble(...) - (double) 1)
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
        // Decide the primitive comparison type from explicit casts and the
        // operand shapes — both sides influence the choice via JLS-style
        // promotion. RHS-side cast is read off ValueAccessConditionValue;
        // RHS literals contribute via their inferred numeric type.
        final ExprType lhsType = inferComparisonOperandType(cc.getLeft(), cc.getLeftCast(), genCtx);
        final ExprType rhsType = inferRightHandType(cc.getRight(), genCtx);
        final ExprType cmpType = pickComparisonType(lhsType, rhsType);
        final Class<?> cmpClass = primitiveClass(cmpType);

        // Generate left side into a buffer to inspect what the codegen
        // produced — typed proto fields and binary-expression results expose
        // their primitive expression via genCtx.lastRawChain, which lets us
        // skip the h.toX() unboxing wrapper. A top-level numeric cast on the
        // comparison (`tag("a") as Integer < 1.5`) is honoured first so the
        // operand is rendered in the user's declared primitive, not in the
        // promoted comparison type.
        final StringBuilder leftBuf = new StringBuilder();
        emitOperandRespectingCast(leftBuf, cc.getLeft(), cc.getLeftCast(), genCtx);

        final Class<?> resolved = genCtx.lastResolvedType;
        final boolean primitiveNumeric = resolved != null
            && (resolved == int.class || resolved == long.class
                || resolved == float.class || resolved == double.class);

        final String leftExpr;
        if (primitiveNumeric && genCtx.lastRawChain != null) {
            // Already primitive — widen to cmpType only if narrower.
            leftExpr = widenPrimitiveExpr(genCtx.lastRawChain,
                primitiveToExprType(resolved), cmpType);
        } else {
            // Untyped operand — wrap with the helper that produces the
            // chosen primitive type. h.toLong is no longer the universal
            // fallback when the user wrote `as Double` / `as Float`.
            leftExpr = wrapAsPrimitive(leftBuf.toString(), cmpType);
        }

        if (genCtx.lastNullChecks != null && primitiveNumeric) {
            sb.append("(").append(genCtx.lastNullChecks).append(" ? false : ")
              .append(leftExpr).append(op);
            generateConditionValueNumeric(sb, cc.getRight(), cmpClass, genCtx);
            sb.append(")");
        } else {
            sb.append(leftExpr).append(op);
            generateConditionValueNumeric(sb, cc.getRight(), cmpClass, genCtx);
        }
    }

    /**
     * Read the numeric type the user declared for the left side of a
     * comparison: top-level {@code as} cast on the comparison wins, otherwise
     * fall back to inspecting the operand AST.
     */
    private static ExprType inferComparisonOperandType(
            final LALScriptModel.ValueAccess value,
            final String topLevelCast,
            final LALClassGenerator.GenCtx genCtx) {
        final ExprType fromCast = castToType(topLevelCast);
        if (fromCast.isNumeric()) {
            return fromCast;
        }
        final ExprType inferred = inferType(value, genCtx);
        return inferred.isNumeric() ? inferred : ExprType.LONG;
    }

    /**
     * Read the numeric type of a comparison's RHS — literal type for number
     * literals, declared cast for value accesses, otherwise unknown.
     */
    private static ExprType inferRightHandType(
            final LALScriptModel.ConditionValue cv,
            final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            final String literal = ((LALScriptModel.NumberConditionValue) cv).getLiteral();
            return literal != null ? NumericLiteral.parse(literal).type : ExprType.LONG;
        }
        if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            final LALScriptModel.ValueAccessConditionValue vacv =
                (LALScriptModel.ValueAccessConditionValue) cv;
            final ExprType castT = castToType(vacv.getCastType());
            if (castT.isNumeric()) {
                return castT;
            }
            return inferType(vacv.getValue(), genCtx);
        }
        return ExprType.UNKNOWN;
    }

    /**
     * Pick the JLS-promoted primitive type for a comparison given both sides'
     * inferred types. Numeric sides drive promotion; non-numeric sides default
     * to {@code LONG} so the comparison still has a primitive home.
     */
    private static ExprType pickComparisonType(final ExprType lhs, final ExprType rhs) {
        if (lhs.isNumeric() && rhs.isNumeric()) {
            return promote(lhs, rhs);
        }
        if (lhs.isNumeric()) {
            return lhs;
        }
        if (rhs.isNumeric()) {
            return rhs;
        }
        return ExprType.LONG;
    }

    /**
     * Emit a primitive widening cast on an already-primitive expression, only
     * when the source type is narrower than the target.
     */
    private static String widenPrimitiveExpr(final String expr,
                                              final ExprType from,
                                              final ExprType to) {
        if (from == to || promote(from, to) == from) {
            return expr;
        }
        return "(" + javaPrimitiveName(to) + ") " + expr;
    }

    /**
     * Render an operand for a numeric comparison while preserving the user's
     * declared cast. Two-phase to avoid double-coercion:
     * <ol>
     *   <li>Render the operand expression. Typed proto fields, arithmetic
     *       results, and paren-cast operands already land as a primitive and
     *       record the type via {@code genCtx.lastResolvedType}.</li>
     *   <li>If the rendered expression is non-primitive (a boxed/Object)
     *       and the user declared a numeric cast, wrap it with the matching
     *       {@code h.toX()} helper so the primitive identity is preserved
     *       — e.g. {@code tag("a") as Integer < 1.5} renders
     *       {@code (double) h.toInt(h.tagValue("a")) < 1.5}, not
     *       {@code h.toDouble(h.tagValue("a")) < 1.5}.</li>
     * </ol>
     */
    private static void emitOperandRespectingCast(
            final StringBuilder sb,
            final LALScriptModel.ValueAccess value,
            final String castType,
            final LALClassGenerator.GenCtx genCtx) {
        final int start = sb.length();
        generateValueAccessObj(sb, value, castType, genCtx);
        final Class<?> resolved = genCtx.lastResolvedType;
        final boolean alreadyPrimitive = resolved != null && resolved.isPrimitive();
        if (alreadyPrimitive) {
            // Native primitive — the declared cast is either redundant
            // (matches) or will be honoured by JLS widening at the
            // comparison site. Leave the rendered expression as-is.
            return;
        }
        final ExprType castET = castToType(castType);
        if (!castET.isNumeric()) {
            return;
        }
        // Wrap with the matching numeric helper. Re-record the primitive
        // metadata so the comparison emits an unboxed compare.
        final String inner = sb.substring(start);
        sb.setLength(start);
        switch (castET) {
            case INT:
                sb.append("h.toInt(").append(inner).append(")");
                recordPrimitiveResult(genCtx, int.class, sb, start);
                break;
            case LONG:
                sb.append("h.toLong(").append(inner).append(")");
                recordPrimitiveResult(genCtx, long.class, sb, start);
                break;
            case FLOAT:
                sb.append("h.toFloat(").append(inner).append(")");
                recordPrimitiveResult(genCtx, float.class, sb, start);
                break;
            case DOUBLE:
                sb.append("h.toDouble(").append(inner).append(")");
                recordPrimitiveResult(genCtx, double.class, sb, start);
                break;
            default:
                // unreachable — castET.isNumeric() checked above
                sb.append(inner);
                break;
        }
    }

    /**
     * Wrap an Object-typed expression in the right {@code h.toX()} helper for
     * the chosen primitive comparison type.
     */
    private static String wrapAsPrimitive(final String objectExpr, final ExprType target) {
        switch (target) {
            case DOUBLE:
                return "h.toDouble(" + objectExpr + ")";
            case FLOAT:
                return "h.toFloat(" + objectExpr + ")";
            case INT:
                return "h.toInt(" + objectExpr + ")";
            case LONG:
            default:
                return "h.toLong(" + objectExpr + ")";
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

    /**
     * Render a numeric RHS for a comparison operator. The literal is emitted
     * in a form compatible with the LHS resolved type so the comparison stays
     * in the LHS's primitive space — no defensive widening to long.
     *
     * <p>Examples (LHS in parens):
     * <ul>
     *   <li>(int) {@code < 10000}   → {@code 10000}</li>
     *   <li>(long) {@code < 10000}  → {@code 10000L}</li>
     *   <li>(double) {@code < 1.5}  → {@code 1.5d}</li>
     *   <li>(int) {@code < 1.5}     → {@code 1.5d} — JLS will widen LHS to double</li>
     * </ul>
     */
    static void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv,
            final Class<?> lhsType,
            final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            final LALScriptModel.NumberConditionValue ncv =
                (LALScriptModel.NumberConditionValue) cv;
            final String literal = ncv.getLiteral();
            if (literal != null) {
                sb.append(formatNumericLiteralForCompare(literal, lhsType));
            } else {
                // Synthesised value (no source text).
                sb.append(formatDoubleAsLiteral(ncv.getValue(), lhsType));
            }
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            // RHS value access — emit the LHS-typed primitive form. If
            // codegen managed to land it as a primitive (typed proto field,
            // arithmetic), reuse lastRawChain widened to lhsType; otherwise
            // wrap the boxed object with the matching h.toX() helper.
            final LALScriptModel.ValueAccessConditionValue vacv =
                (LALScriptModel.ValueAccessConditionValue) cv;
            final StringBuilder rhsBuf = new StringBuilder();
            emitOperandRespectingCast(rhsBuf, vacv.getValue(), vacv.getCastType(), genCtx);
            final Class<?> rhsResolved = genCtx.lastResolvedType;
            final ExprType target = primitiveToExprType(lhsType);
            if (rhsResolved != null
                    && (rhsResolved == int.class || rhsResolved == long.class
                        || rhsResolved == float.class || rhsResolved == double.class)
                    && genCtx.lastRawChain != null) {
                sb.append(widenPrimitiveExpr(genCtx.lastRawChain,
                    primitiveToExprType(rhsResolved), target));
            } else {
                sb.append(wrapAsPrimitive(rhsBuf.toString(), target));
            }
        } else {
            sb.append("0L");
        }
    }

    private static String formatNumericLiteralForCompare(final String literal,
                                                          final Class<?> lhsType) {
        final NumericLiteral parsed = NumericLiteral.parse(literal);
        // Honour the user's declared literal type when wider than LHS;
        // otherwise emit in the LHS form so the compare stays in that space.
        final ExprType lhsExpr = primitiveToExprType(lhsType);
        final ExprType result = parsed.type.compareTo(lhsExpr) > 0 ? parsed.type : lhsExpr;
        if (result == parsed.type) {
            return parsed.javaText;
        }
        // Widen literal to LHS type via suffix change (no Java cast needed for
        // numeric literals — the widened form is itself a valid literal).
        return widenLiteralToType(parsed.javaText, parsed.type, result);
    }

    private static String widenLiteralToType(final String text,
                                              final ExprType from,
                                              final ExprType to) {
        // Strip trailing suffix (if any) and re-emit in `to`.
        String body = text;
        final char last = body.isEmpty() ? 0 : body.charAt(body.length() - 1);
        if (last == 'L' || last == 'F' || last == 'f' || last == 'd' || last == 'D') {
            body = body.substring(0, body.length() - 1);
        }
        switch (to) {
            case LONG:
                return body + "L";
            case FLOAT:
                return (body.contains(".") ? body : body + ".0") + "f";
            case DOUBLE:
                return body.contains(".") ? body : body + ".0";
            case INT:
            default:
                return body;
        }
    }

    private static String formatDoubleAsLiteral(final double value, final Class<?> lhsType) {
        if (lhsType == double.class || lhsType == float.class) {
            return Double.toString(value) + (lhsType == float.class ? "f" : "");
        }
        if (lhsType == long.class) {
            return ((long) value) + "L";
        }
        return Long.toString((long) value);
    }

    private static ExprType primitiveToExprType(final Class<?> c) {
        if (c == int.class) {
            return ExprType.INT;
        }
        if (c == long.class) {
            return ExprType.LONG;
        }
        if (c == float.class) {
            return ExprType.FLOAT;
        }
        if (c == double.class) {
            return ExprType.DOUBLE;
        }
        return ExprType.LONG;
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
        // Each numeric cast produces a primitive expression; record the type
        // and the raw chain so callers (numeric comparisons, arithmetic
        // codegen) can skip a redundant h.toX() wrapper when the operand
        // already matches the target primitive.
        final int start = sb.length();
        if ("String".equals(castType)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else if ("Long".equals(castType)) {
            sb.append("h.toLong(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
            recordPrimitiveResult(genCtx, long.class, sb, start);
        } else if ("Integer".equals(castType)) {
            sb.append("h.toInt(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
            recordPrimitiveResult(genCtx, int.class, sb, start);
        } else if ("Double".equals(castType)) {
            sb.append("h.toDouble(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
            recordPrimitiveResult(genCtx, double.class, sb, start);
        } else if ("Float".equals(castType)) {
            sb.append("h.toFloat(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
            recordPrimitiveResult(genCtx, float.class, sb, start);
        } else if ("Boolean".equals(castType)) {
            sb.append("h.toBool(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    private static void recordPrimitiveResult(final LALClassGenerator.GenCtx genCtx,
                                                final Class<?> type,
                                                final StringBuilder sb,
                                                final int start) {
        genCtx.lastResolvedType = type;
        genCtx.lastRawChain = sb.substring(start);
        genCtx.lastNullChecks = null;
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
        } else if ("Double".equals(castType)) {
            sb.append("String.valueOf(h.toDouble(");
            generateValueAccess(sb, value, genCtx);
            sb.append("))");
        } else if ("Float".equals(castType)) {
            sb.append("String.valueOf(h.toFloat(");
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

        // Handle binary expressions (term1 OP term2 OP ...). The decision is
        // delegated to generateBinaryExpression, which performs JLS-style type
        // inference and emits either Java arithmetic, string concatenation, or a
        // compile-time error.
        if (!value.getConcatParts().isEmpty()) {
            generateBinaryExpression(sb, value.getConcatParts(),
                value.getConcatOps(), genCtx);
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

        // sourceAttribute("KEY") — reads from LogMetadata.sourceAttributes (non-persistent).
        // Available regardless of input type (reads from metadata, not input).
        if ("sourceAttribute".equals(value.getFunctionCallName())) {
            if (value.getFunctionCallArgs().size() != 1
                    || !value.getFunctionCallArgs().get(0).getValue().isStringLiteral()) {
                throw new IllegalArgumentException(
                    "sourceAttribute() requires exactly one string literal argument, "
                        + "e.g. sourceAttribute(\"os.name\")");
            }
            sb.append("h.sourceAttributeValue(\"");
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
            final NumericLiteral lit = NumericLiteral.parse(num);
            switch (lit.type) {
                case DOUBLE:
                    sb.append("Double.valueOf(").append(lit.javaText).append(")");
                    break;
                case FLOAT:
                    sb.append("Float.valueOf(").append(lit.javaText).append(")");
                    break;
                case LONG:
                    sb.append("Long.valueOf(").append(lit.javaText).append(")");
                    break;
                case INT:
                default:
                    sb.append("Integer.valueOf(").append(lit.javaText).append(")");
                    break;
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

    // ==================== Binary expression codegen ====================

    /**
     * JLS-style numeric type tag used for binary numeric promotion. Ordered
     * narrow → wide, so {@code commonType(a, b)} can return the wider of two.
     */
    private enum ExprType {
        INT, LONG, FLOAT, DOUBLE,
        STRING, BOOLEAN, OBJECT, UNKNOWN;

        boolean isNumeric() {
            return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
        }
    }

    /**
     * Parsed representation of a NUMBER literal token. Carries the inferred
     * Java type and the literal text trimmed/suffixed for direct emission as
     * Java source.
     */
    private static final class NumericLiteral {
        final ExprType type;
        /** Java-source representation, e.g. "10000", "10000L", "1.5", "1.5f". */
        final String javaText;

        private NumericLiteral(final ExprType type, final String javaText) {
            this.type = type;
            this.javaText = javaText;
        }

        static NumericLiteral parse(final String numText) {
            String t = numText;
            char suffix = 0;
            if (!t.isEmpty()) {
                final char last = t.charAt(t.length() - 1);
                if (last == 'L' || last == 'l' || last == 'F' || last == 'f'
                        || last == 'D' || last == 'd') {
                    suffix = last;
                    t = t.substring(0, t.length() - 1);
                }
            }
            final boolean fractional = t.contains(".") || t.contains("e") || t.contains("E");
            switch (suffix) {
                case 'L':
                case 'l':
                    return new NumericLiteral(ExprType.LONG, t + "L");
                case 'F':
                case 'f':
                    return new NumericLiteral(ExprType.FLOAT,
                        (fractional ? t : (t + ".0")) + "f");
                case 'D':
                case 'd':
                    return new NumericLiteral(ExprType.DOUBLE,
                        (fractional ? t : (t + ".0")) + "d");
                default:
                    if (fractional) {
                        return new NumericLiteral(ExprType.DOUBLE, t);
                    }
                    // Bare integer literal — INT if it fits, else LONG.
                    try {
                        Integer.parseInt(t);
                        return new NumericLiteral(ExprType.INT, t);
                    } catch (NumberFormatException e) {
                        return new NumericLiteral(ExprType.LONG, t + "L");
                    }
            }
        }
    }

    /**
     * Returns the JLS-style binary numeric promotion result for the two operand
     * types. Both must be numeric; otherwise this is a programmer bug (the
     * caller is responsible for the non-numeric error path).
     */
    private static ExprType promote(final ExprType a, final ExprType b) {
        if (a == ExprType.DOUBLE || b == ExprType.DOUBLE) {
            return ExprType.DOUBLE;
        }
        if (a == ExprType.FLOAT || b == ExprType.FLOAT) {
            return ExprType.FLOAT;
        }
        if (a == ExprType.LONG || b == ExprType.LONG) {
            return ExprType.LONG;
        }
        return ExprType.INT;
    }

    private static String javaPrimitiveName(final ExprType t) {
        switch (t) {
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case INT:
            default:
                return "int";
        }
    }

    private static String javaWrapperName(final ExprType t) {
        switch (t) {
            case LONG:
                return "Long";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case INT:
            default:
                return "Integer";
        }
    }

    private static Class<?> primitiveClass(final ExprType t) {
        switch (t) {
            case LONG:
                return long.class;
            case FLOAT:
                return float.class;
            case DOUBLE:
                return double.class;
            case INT:
            default:
                return int.class;
        }
    }

    /**
     * Compile-time inference of an operand's Java type. Operates on the same
     * AST that codegen will subsequently emit, so the result is consistent.
     */
    private static ExprType inferType(final LALScriptModel.ValueAccess part,
                                       final LALClassGenerator.GenCtx genCtx) {
        if (part == null) {
            return ExprType.UNKNOWN;
        }
        if (part.isStringLiteral()) {
            return ExprType.STRING;
        }
        if (part.isNumberLiteral() && part.getChain().isEmpty()) {
            return NumericLiteral.parse(part.getSegments().get(0)).type;
        }
        // (expr as Cast) primary — only trustable when no further chain is applied.
        if (part.getParenInner() != null && part.getChain().isEmpty()) {
            if (part.getParenCast() != null) {
                return castToType(part.getParenCast());
            }
            // Pure grouping (no cast): the parens just preserve precedence.
            return inferType(part.getParenInner(), genCtx);
        }
        // Nested binary expression: recompute via its parts/ops.
        if (!part.getConcatParts().isEmpty()) {
            return inferBinaryType(part.getConcatParts(), part.getConcatOps(), genCtx);
        }
        // def variable with a known resolved type.
        if (!part.getSegments().isEmpty()) {
            final LALClassGenerator.LocalVarInfo lv =
                genCtx.localVars.get(part.getSegments().get(0));
            if (lv != null && part.getChain().isEmpty()) {
                final Class<?> t = lv.resolvedType;
                if (t == int.class || t == Integer.class) {
                    return ExprType.INT;
                }
                if (t == long.class || t == Long.class) {
                    return ExprType.LONG;
                }
                if (t == float.class || t == Float.class) {
                    return ExprType.FLOAT;
                }
                if (t == double.class || t == Double.class) {
                    return ExprType.DOUBLE;
                }
                if (t == String.class) {
                    return ExprType.STRING;
                }
                if (t == boolean.class || t == Boolean.class) {
                    return ExprType.BOOLEAN;
                }
                return ExprType.OBJECT;
            }
        }
        // tag(), sourceAttribute(), parsed.* without typed input — String.
        if ("tag".equals(part.getFunctionCallName())
                || "sourceAttribute".equals(part.getFunctionCallName())) {
            return ExprType.STRING;
        }
        return ExprType.UNKNOWN;
    }

    private static ExprType castToType(final String cast) {
        if ("Integer".equals(cast)) {
            return ExprType.INT;
        }
        if ("Long".equals(cast)) {
            return ExprType.LONG;
        }
        if ("Float".equals(cast)) {
            return ExprType.FLOAT;
        }
        if ("Double".equals(cast)) {
            return ExprType.DOUBLE;
        }
        if ("String".equals(cast)) {
            return ExprType.STRING;
        }
        if ("Boolean".equals(cast)) {
            return ExprType.BOOLEAN;
        }
        return ExprType.UNKNOWN;
    }

    /**
     * Walk the operand list applying JLS promotion left-to-right, treating any
     * {@code +} that touches a String operand as string concat (which sticks).
     */
    private static ExprType inferBinaryType(
            final List<LALScriptModel.ValueAccess> parts,
            final List<LALScriptModel.BinaryOp> ops,
            final LALClassGenerator.GenCtx genCtx) {
        ExprType acc = inferType(parts.get(0), genCtx);
        for (int i = 1; i < parts.size(); i++) {
            final ExprType rhs = inferType(parts.get(i), genCtx);
            final LALScriptModel.BinaryOp op = ops.get(i - 1);
            if (op == LALScriptModel.BinaryOp.PLUS
                    && (acc == ExprType.STRING || rhs == ExprType.STRING)) {
                acc = ExprType.STRING;
                continue;
            }
            if (acc.isNumeric() && rhs.isNumeric()) {
                acc = promote(acc, rhs);
                continue;
            }
            // Anything else for + (object + object) → fallback to string concat
            // (preserves long-standing behaviour for `tag("a") + tag("b")` etc.).
            if (op == LALScriptModel.BinaryOp.PLUS) {
                acc = ExprType.STRING;
                continue;
            }
            // - * / on non-numeric — caller emits a compile error.
            return ExprType.UNKNOWN;
        }
        return acc;
    }

    /**
     * Top-level binary-expression codegen. Branches by the inferred result type:
     * numeric → Java arithmetic in the promoted primitive; STRING → string
     * concat (only valid if the only operators are {@code +}); UNKNOWN with a
     * non-{@code +} operator → compile-time error.
     */
    private static void generateBinaryExpression(
            final StringBuilder sb,
            final List<LALScriptModel.ValueAccess> parts,
            final List<LALScriptModel.BinaryOp> ops,
            final LALClassGenerator.GenCtx genCtx) {
        final ExprType resultType = inferBinaryType(parts, ops, genCtx);
        if (resultType.isNumeric()) {
            generateNumericExpression(sb, parts, ops, resultType, genCtx);
            return;
        }
        // Validate operators — only `+` is legal for string concat.
        for (final LALScriptModel.BinaryOp op : ops) {
            if (op != LALScriptModel.BinaryOp.PLUS) {
                throw new IllegalArgumentException(
                    "Operator '" + opSymbol(op) + "' requires numeric operands; "
                        + "got non-numeric expression. Cast operands with "
                        + "'as Integer/Long/Float/Double' to enable arithmetic.");
            }
        }
        generateStringConcat(sb, parts, genCtx);
    }

    private static String opSymbol(final LALScriptModel.BinaryOp op) {
        switch (op) {
            case PLUS:
                return "+";
            case MINUS:
                return "-";
            case STAR:
                return "*";
            case SLASH:
                return "/";
            default:
                return "?";
        }
    }

    /**
     * Emit Java arithmetic in the promoted primitive type. Each operand is
     * widened only when its declared type differs from the result type (no
     * defensive INT → LONG bumps — respect the user's declared casts).
     *
     * <p>Two outputs are produced:
     * <ul>
     *   <li>The raw primitive expression is stored in
     *       {@code genCtx.lastRawChain}, with {@code lastResolvedType} set
     *       to the promoted primitive class. This lets
     *       {@link #generateNumericComparison} emit an unboxed comparison.</li>
     *   <li>{@code Wrapper.valueOf(rawExpr)} is appended to {@code sb} so
     *       Object contexts (tag assignment, def initialiser, {@code h.toStr}
     *       wrapping, etc.) receive a properly boxed value.</li>
     * </ul>
     */
    private static void generateNumericExpression(
            final StringBuilder sb,
            final List<LALScriptModel.ValueAccess> parts,
            final List<LALScriptModel.BinaryOp> ops,
            final ExprType resultType,
            final LALClassGenerator.GenCtx genCtx) {
        final StringBuilder expr = new StringBuilder();
        appendOperand(expr, parts.get(0), resultType, genCtx);
        for (int i = 1; i < parts.size(); i++) {
            expr.append(' ').append(opSymbol(ops.get(i - 1))).append(' ');
            appendOperand(expr, parts.get(i), resultType, genCtx);
        }
        final String rawExpr = "(" + expr + ")";
        genCtx.lastResolvedType = primitiveClass(resultType);
        genCtx.lastRawChain = rawExpr;
        genCtx.lastNullChecks = null;
        sb.append(javaWrapperName(resultType)).append(".valueOf(").append(rawExpr).append(")");
    }

    /**
     * Render one numeric operand into the promoted result type, casting only
     * when the operand's declared type is narrower than the result.
     */
    private static void appendOperand(final StringBuilder sb,
                                       final LALScriptModel.ValueAccess part,
                                       final ExprType resultType,
                                       final LALClassGenerator.GenCtx genCtx) {
        final ExprType opType = inferType(part, genCtx);
        // Number literal — emit in its source form, widened by suffix when needed.
        if (part.isNumberLiteral() && part.getChain().isEmpty()) {
            final NumericLiteral lit = NumericLiteral.parse(part.getSegments().get(0));
            if (lit.type == resultType) {
                sb.append(lit.javaText);
                return;
            }
            // Widen via Java cast — e.g. INT literal `10000` in DOUBLE context → `(double) 10000`.
            sb.append("(").append(javaPrimitiveName(resultType)).append(") ").append(lit.javaText);
            return;
        }
        // Paren-cast operand — emit the cast call directly.
        if (part.getParenInner() != null && part.getParenCast() != null
                && part.getChain().isEmpty() && opType.isNumeric()) {
            if (opType == resultType) {
                generateCastedValueAccess(sb, part.getParenInner(),
                    javaWrapperName(opType), genCtx);
            } else {
                sb.append("(").append(javaPrimitiveName(resultType)).append(") ");
                generateCastedValueAccess(sb, part.getParenInner(),
                    javaWrapperName(opType), genCtx);
            }
            return;
        }
        // Paren grouping without cast — recurse into the inner expression
        // and unwrap to its primitive raw chain. Inner is typically a
        // sub-arithmetic like (1 + 2) or a typed proto comparison.
        if (part.getParenInner() != null && part.getParenCast() == null
                && part.getChain().isEmpty() && opType.isNumeric()) {
            final StringBuilder inner = new StringBuilder();
            generateValueAccessObj(inner, part.getParenInner(), null, genCtx);
            final Class<?> resolved = genCtx.lastResolvedType;
            final String rawChain = genCtx.lastRawChain;
            // Inner produced a primitive — reuse its raw chain.
            if (rawChain != null && resolved != null
                    && (resolved == int.class || resolved == long.class
                        || resolved == float.class || resolved == double.class)) {
                final ExprType from = primitiveToExprType(resolved);
                sb.append(widenPrimitiveExpr(rawChain, from, resultType));
            } else {
                // Inner stayed boxed — wrap to the result primitive.
                sb.append(wrapAsPrimitive(inner.toString(), resultType));
            }
            return;
        }
        // Nested binary expression — recurse, then unbox via the raw chain.
        if (!part.getConcatParts().isEmpty()) {
            final StringBuilder inner = new StringBuilder();
            generateBinaryExpression(inner, part.getConcatParts(), part.getConcatOps(), genCtx);
            // generateNumericExpression set lastRawChain to the primitive expression.
            final String rawInner = genCtx.lastRawChain;
            if (opType == resultType) {
                sb.append(rawInner);
            } else {
                sb.append("(").append(javaPrimitiveName(resultType)).append(") ").append(rawInner);
            }
            // Don't let the inner expression's lastRawChain leak as the
            // outermost result — the caller will overwrite it after this call.
            return;
        }
        // def variable or other resolved value — fall back to a value access
        // wrapped with the appropriate cast to the result type.
        sb.append("(").append(javaPrimitiveName(resultType)).append(") ");
        generateCastedValueAccess(sb, part, javaWrapperName(resultType), genCtx);
    }

    /**
     * Emit a Java string concatenation: {@code ("" + a + b + ...)}. Used for
     * legacy GString-style joins where at least one operand is a String (or
     * UNKNOWN/OBJECT) and every operator is {@code +}.
     */
    private static void generateStringConcat(final StringBuilder sb,
                                              final List<LALScriptModel.ValueAccess> parts,
                                              final LALClassGenerator.GenCtx genCtx) {
        sb.append("(\"\" + ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            generateValueAccess(sb, parts.get(i), genCtx);
        }
        sb.append(")");
    }

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
