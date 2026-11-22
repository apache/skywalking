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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;

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
    static final String PROCESS_REGISTRY = ProcessRegistry.class.getName();

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
     * if (parsed.code == 200)
     * → h.equal(h.mapVal("code"), Integer.valueOf(200))
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
     * → h.isNotEmpty(h.toStr(...))
     * }</pre>
     */
    static void generateCondition(final StringBuilder sb,
                                   final LALScriptModel.Condition cond,
                                   final LALClassGenerator.GenCtx genCtx) {
        if (cond instanceof LALScriptModel.ComparisonCondition) {
            final LALScriptModel.ComparisonCondition cc =
                (LALScriptModel.ComparisonCondition) cond;
            if (isNumericComparison(cc, genCtx)) {
                generateNumericComparison(sb, cc, genCtx);
            } else if (isEquality(cc)) {
                generateEquality(sb, cc, genCtx);
            } else {
                rejectUnorderable(cc, genCtx);
                generateRuntimeOrder(sb, cc, genCtx);
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
            final LALScriptModel.ValueAccess expr = ((LALScriptModel.ExprCondition) cond).getExpr();
            if (castToType(ct) == ExprType.BOOLEAN) {
                sb.append("h.toBool(");
                generateValueAccess(sb, expr, genCtx);
            } else {
                // Groovy truth of the value, converted first when cast.
                sb.append("h.isNotEmpty(");
                generateBoxedValue(sb, expr, ct, genCtx);
            }
            sb.append(")");
        } else {
            throw new IllegalStateException("Unhandled condition " + cond.getClass().getSimpleName());
        }
    }

    /**
     * Renders an operand as an object honouring its {@code as} cast the way Groovy converts: {@code null} stays
     * {@code null}, so {@code (x as Integer) == null} still holds for a missing {@code x}.
     */
    static void generateBoxedValue(final StringBuilder sb,
                                   final LALScriptModel.ValueAccess value,
                                   final String castType,
                                   final LALClassGenerator.GenCtx genCtx) {
        final ExprType castET = castToType(castType);
        if (castET == ExprType.STRING) {
            generateValueAccessObj(sb, value, castType, genCtx);
            return;
        }
        if (!castET.isNumeric() && castET != ExprType.BOOLEAN) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        final StringBuilder raw = new StringBuilder();
        generateValueAccess(raw, value, genCtx);
        rejectUnconvertibleCast(value, castET, genCtx);
        sb.append(castObject(castType, raw.toString(), false));
        genCtx.clearExtraLogResult();
        if (LALCodegenHelper.PRIMITIVE_CASTS.contains(castType)) {
            genCtx.lastNonNull = true;
        }
    }

    /**
     * A built-in cast of a value, as an object, through its runtime helper: a boxed cast keeps a null
     * ({@code h.asInteger}) unless the value is never null, a primitive cast fails on one (a boolean's is false).
     *
     * @return the conversion, or null for a class cast
     */
    static String castObject(final String castType, final String raw, final boolean rawNonNull) {
        final ExprType castET = castToType(castType);
        if (castET == ExprType.STRING) {
            return castET.runtimeHelper + "(" + raw + ")";
        }
        if (!castET.isNumeric() && castET != ExprType.BOOLEAN) {
            return null;
        }
        if (rawNonNull || LALCodegenHelper.PRIMITIVE_CASTS.contains(castType)) {
            return primitiveCastObject(castET, raw);
        }
        return "h.as" + castET.wrapperName + "(" + raw + ")";
    }

    /**
     * The runtime helper converting a value to a primitive ({@code h.toInt}, ..., {@code h.toBool}), or null for
     * another type.
     */
    static String conversionHelper(final Class<?> primitive) {
        if (!primitive.isPrimitive()) {
            return null;
        }
        final ExprType type = exprTypeFromClass(primitive);
        return type.isNumeric() || type == ExprType.BOOLEAN ? type.runtimeHelper : null;
    }

    /**
     * The runtime helper of a numeric cast ({@code as Long}, {@code as int}, ...), or null for another cast.
     */
    static String numericConversionHelper(final String castType) {
        final ExprType type = castToType(castType);
        return type.isNumeric() ? type.runtimeHelper : null;
    }

    /**
     * A cast of a value just rendered (a {@code def}'s, a metric's): see
     * {@link #rejectUnconvertibleCast(LALScriptModel.ValueAccess, ExprType, LALClassGenerator.GenCtx)}.
     */
    static void rejectUnconvertibleCast(final LALScriptModel.ValueAccess value, final String castType,
                                    final LALClassGenerator.GenCtx genCtx) {
        rejectUnconvertibleCast(value, castToType(castType), genCtx);
    }

    /**
     * Refuses a character or a boolean cast as a number: Groovy read a character as its code ({@code '5' as Integer}
     * is 53), where the string conversion names the number it is, and failed a boolean's.
     */
    private static void rejectUnconvertibleCast(final LALScriptModel.ValueAccess value, final ExprType castET,
                                            final LALClassGenerator.GenCtx genCtx) {
        if (!castET.isNumeric()) {
            return;
        }
        final Class<?> type = genCtx.lastResolvedType;
        final String described = value.getParenInner() != null ? "(" + value.getParenInner().toPathString() + " as "
            + value.getParenCast() + ")" : value.toPathString();
        if (type == char.class || type == Character.class) {
            throw DslNumbers.characterAsNumber(described, castET.wrapperName);
        }
        if (type == boolean.class || type == Boolean.class) {
            // Groovy failed the cast.
            throw new IllegalArgumentException(
                "A boolean cannot be cast as a number: " + described + " as " + castET.wrapperName);
        }
    }

    /**
     * A comparison whose sides are both numbers when compiled ({@link #isNumericComparison}): in the wider of their
     * types, as Java promotes it, each side rendered in the type its own cast declares; as Java compares primitives, a
     * NaN compares false (except {@code !=}) and -0.0 equals 0.0.
     */
    static void generateNumericComparison(
            final StringBuilder sb,
            final LALScriptModel.ComparisonCondition cc,
            final LALClassGenerator.GenCtx genCtx) {
        final ExprType cmpType = promote(numericType(cc.getLeft(), cc.getLeftCast(), genCtx),
            numericType(cc.getRight(), genCtx));

        // The null guards each side produced are read before the other side's rendering overwrites them.
        genCtx.clearExtraLogResult();
        final String leftExpr = renderAsPrimitive(
            cc.getLeft(), cc.getLeftCast(), cmpType, genCtx);
        final String lhsNullChecks = genCtx.lastNullChecks;
        final boolean lhsNullableObject = genCtx.lastNullCheckOnObject;
        final boolean lhsPure = genCtx.lastPure;

        genCtx.clearExtraLogResult();
        final StringBuilder rhsBuf = new StringBuilder();
        generateConditionValueNumeric(rhsBuf, cc.getRight(), cmpType, genCtx);
        final String rhsNullChecks = genCtx.lastNullChecks;
        final boolean rhsNullableObject = genCtx.lastNullCheckOnObject;
        final boolean rhsPure = genCtx.lastPure;

        // A side converted from an object that may be null is compared as Groovy compares objects, both sides
        // converted once and in order, unless it is a plain read against a literal, which a null check can guard.
        final boolean lhsLiteral = isPlainNumberLiteral(cc.getLeft());
        final boolean rhsLiteral = cc.getRight() instanceof LALScriptModel.NumberConditionValue
            || cc.getRight() instanceof LALScriptModel.ValueAccessConditionValue
                && isPlainNumberLiteral(((LALScriptModel.ValueAccessConditionValue) cc.getRight()).getValue());
        if (lhsNullableObject && !(lhsPure && rhsLiteral) || rhsNullableObject && !(rhsPure && lhsLiteral)) {
            if (isEquality(cc)) {
                generateEquality(sb, cc, genCtx);
            } else {
                generateRuntimeOrder(sb, cc, genCtx);
            }
            return;
        }

        // A null side orders below every value and equals only a null, as Groovy compares it.
        final LALScriptModel.CompareOp op = cc.getOp();
        final String compare = leftExpr + " " + op.symbol() + " " + rhsBuf;
        if (lhsNullChecks == null && rhsNullChecks == null) {
            sb.append(compare);
        } else if (rhsNullChecks == null) {
            sb.append("(").append(lhsNullChecks).append(" ? ").append(op.holds(-1))
              .append(" : ").append(compare).append(")");
        } else if (lhsNullChecks == null) {
            sb.append("(").append(rhsNullChecks).append(" ? ").append(op.holds(1))
              .append(" : ").append(compare).append(")");
        } else if (lhsNullChecks.equals(rhsNullChecks)) {
            sb.append("(").append(lhsNullChecks).append(" ? ").append(op.holds(0))
              .append(" : ").append(compare).append(")");
        } else {
            sb.append("(").append(lhsNullChecks).append(" ? (").append(rhsNullChecks).append(" ? ")
              .append(op.holds(0)).append(" : ").append(op.holds(-1)).append(") : (")
              .append(rhsNullChecks).append(" ? ").append(op.holds(1)).append(" : ")
              .append(compare).append("))");
        }
    }

    private static boolean isEquality(final LALScriptModel.ComparisonCondition cc) {
        return cc.getOp() == LALScriptModel.CompareOp.EQ || cc.getOp() == LALScriptModel.CompareOp.NEQ;
    }

    /**
     * {@code ==} and {@code !=}: Java's equality when both sides are strings or one is the null literal, else the
     * content equality every DSL shares ({@code h.equal}).
     */
    private static void generateEquality(final StringBuilder sb,
                                         final LALScriptModel.ComparisonCondition cc,
                                         final LALClassGenerator.GenCtx genCtx) {
        sb.append(cc.getOp() == LALScriptModel.CompareOp.NEQ ? "!" : "")
          .append(bothStrings(cc, genCtx) ? "java.util.Objects.equals(" : "h.equal(");
        generateBoxedValue(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
        sb.append(", ");
        generateConditionValue(sb, cc.getRight(), genCtx);
        sb.append(")");
    }

    /**
     * An ordering whose sides are not both numbers when compiled: the values ordered at run time, a NaN comparing
     * false, as Java compares it.
     */
    private static void generateRuntimeOrder(final StringBuilder sb,
                                             final LALScriptModel.ComparisonCondition cc,
                                             final LALClassGenerator.GenCtx genCtx) {
        sb.append(cc.getOp().helper()).append("(");
        generateBoxedValue(sb, cc.getLeft(), cc.getLeftCast(), genCtx);
        sb.append(", ");
        generateConditionValue(sb, cc.getRight(), genCtx);
        sb.append(")");
    }

    /**
     * Whether both sides of a comparison are numbers when compiled, by a cast or by type, so it compares primitives
     * rather than objects. A number literal on the right is one.
     */
    private static boolean isNumericComparison(
            final LALScriptModel.ComparisonCondition cc,
            final LALClassGenerator.GenCtx genCtx) {
        if (numericType(cc.getLeft(), cc.getLeftCast(), genCtx) == null) {
            return false;
        }
        if (cc.getRight() instanceof LALScriptModel.NumberConditionValue) {
            return true;
        }
        if (!(cc.getRight() instanceof LALScriptModel.ValueAccessConditionValue)) {
            return false;
        }
        final LALScriptModel.ValueAccessConditionValue vacv =
            (LALScriptModel.ValueAccessConditionValue) cc.getRight();
        return numericType(vacv.getValue(), vacv.getCastType(), genCtx) != null;
    }

    private static boolean isPlainNumberLiteral(final LALScriptModel.ValueAccess value) {
        return value.isNumberLiteral() && value.isBareLiteral();
    }

    /**
     * @return whether both sides are Strings, or one is the null literal, so Java's equality is the text's; a text of
     *     another CharSequence type (a StringBuilder an input returns) compares by its content instead
     */
    private static boolean bothStrings(final LALScriptModel.ComparisonCondition cc,
                                       final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ConditionValue right = cc.getRight();
        if (right instanceof LALScriptModel.NullConditionValue) {
            return true;
        }
        final boolean rightString = right instanceof LALScriptModel.StringConditionValue
            || right instanceof LALScriptModel.ValueAccessConditionValue
                && isString(((LALScriptModel.ValueAccessConditionValue) right).getValue(),
                    ((LALScriptModel.ValueAccessConditionValue) right).getCastType(), genCtx);
        return rightString && isString(cc.getLeft(), cc.getLeftCast(), genCtx);
    }

    private static boolean isString(final LALScriptModel.ValueAccess value, final String cast,
                                    final LALClassGenerator.GenCtx genCtx) {
        return cast != null ? "String".equals(cast) : inferType(value, genCtx) == ExprType.STRING;
    }

    /**
     * An ordering that cannot hold: a string literal holding no number against a number.
     */
    private static void rejectUnorderable(final LALScriptModel.ComparisonCondition cc,
                                          final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ConditionValue right = cc.getRight();
        final boolean leftNumber = isKnownNumber(cc.getLeft(), cc.getLeftCast(), genCtx);
        final boolean rightNumber = right instanceof LALScriptModel.NumberConditionValue
            || right instanceof LALScriptModel.ValueAccessConditionValue
                && isKnownNumber(((LALScriptModel.ValueAccessConditionValue) right).getValue(),
                    ((LALScriptModel.ValueAccessConditionValue) right).getCastType(), genCtx);
        final String leftLiteral = "String".equals(cc.getLeftCast()) || cc.getLeftCast() == null
            ? stringLiteralOf(cc.getLeft()) : null;
        final String rightLiteral = right instanceof LALScriptModel.StringConditionValue
            ? ((LALScriptModel.StringConditionValue) right).getValue()
            : right instanceof LALScriptModel.ValueAccessConditionValue
                && (((LALScriptModel.ValueAccessConditionValue) right).getCastType() == null
                    || "String".equals(((LALScriptModel.ValueAccessConditionValue) right).getCastType()))
                ? stringLiteralOf(((LALScriptModel.ValueAccessConditionValue) right).getValue()) : null;
        final String notANumber = rightNumber && leftLiteral != null && !holdsANumber(leftLiteral) ? leftLiteral
            : leftNumber && rightLiteral != null && !holdsANumber(rightLiteral) ? rightLiteral : null;
        if (notANumber != null) {
            throw new IllegalArgumentException(
                "The string '" + notANumber + "' is not a number, so it cannot be ordered against one ("
                    + cc.getOp().symbol() + ")");
        }
    }

    private static String stringLiteralOf(final LALScriptModel.ValueAccess value) {
        final LALScriptModel.ValueAccess grouped = value.groupedInner();
        if (grouped != null) {
            return stringLiteralOf(grouped);
        }
        return isPlainStringLiteral(value) ? value.getSegments().get(0) : null;
    }

    /**
     * Whether a value is a number when compiled: a numeric cast, or a static type that is a number, such as a
     * quotient's, a {@code BigInteger} def's or {@code log.timestamp}'s.
     */
    private static boolean isKnownNumber(final LALScriptModel.ValueAccess value, final String cast,
                                         final LALClassGenerator.GenCtx genCtx) {
        if (cast != null) {
            return castToType(cast).isNumeric();
        }
        final LALScriptModel.ValueAccess grouped = value.groupedInner();
        if (grouped != null) {
            return isKnownNumber(grouped, null, genCtx);
        }
        final Class<?> type = staticType(value, genCtx);
        return type != null && Number.class.isAssignableFrom(DslMethods.boxed(type));
    }

    private static boolean holdsANumber(final String literal) {
        return DslNumbers.numberIn(literal) != null;
    }

    /**
     * @return the numeric type of an operand, by its cast or else by its static type; null when it is no number
     */
    private static ExprType numericType(
            final LALScriptModel.ValueAccess value,
            final String topLevelCast,
            final LALClassGenerator.GenCtx genCtx) {
        final ExprType type = topLevelCast != null ? castToType(topLevelCast) : inferType(value, genCtx);
        return type.isNumeric() ? type : null;
    }

    /**
     * @return the numeric type of a comparison's right side, which {@link #isNumericComparison} found a number
     */
    private static ExprType numericType(final LALScriptModel.ConditionValue cv,
                                        final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            return LALLiterals.NumericLiteral.parse(((LALScriptModel.NumberConditionValue) cv).getLiteral()).type;
        }
        if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            final LALScriptModel.ValueAccessConditionValue vacv = (LALScriptModel.ValueAccessConditionValue) cv;
            return numericType(vacv.getValue(), vacv.getCastType(), genCtx);
        }
        throw new IllegalStateException("A " + cv.getClass().getSimpleName() + " is no number");
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
        return "(" + to.primitiveKeyword + ") " + expr;
    }

    /**
     * Renders an operand for a numeric comparison in the primitive its own cast declares, so
     * {@code tag("a") as Integer < 1.5} compares {@code (double) h.toInt(h.tagValue("a"))}, not
     * {@code h.toDouble(h.tagValue("a"))}: a primitive value is cast to it, an object converted by its helper.
     */
    private static void emitOperandRespectingCast(
            final StringBuilder sb,
            final LALScriptModel.ValueAccess value,
            final String castType,
            final LALClassGenerator.GenCtx genCtx) {
        final int start = sb.length();
        generateValueAccessObj(sb, value, castType, genCtx);
        final Class<?> resolved = genCtx.lastResolvedType;
        final ExprType castET = castToType(castType);
        rejectUnconvertibleCast(value, castET, genCtx);
        if (resolved != null && resolved.isPrimitive()) {
            if (castType != null && LALCodegenHelper.PRIMITIVE_CASTS.contains(castType)) {
                // An explicit primitive cast fails on a null rather than ordering it first.
                genCtx.lastNullChecks = null;
                genCtx.lastNullCheckOnObject = false;
            }
            if (castET.isNumeric()
                    && exprTypeFromClass(resolved) != castET
                    && genCtx.lastRawChain != null) {
                // The primitive form cast, as sb may hold its box; a typed-proto ?. chain's null guard is kept.
                final String preservedNullChecks = genCtx.lastNullChecks;
                final String primExpr = genCtx.lastRawChain;
                sb.setLength(start);
                sb.append("(").append(castET.primitiveKeyword)
                  .append(") (").append(primExpr).append(")");
                recordPrimitiveResult(genCtx, castET.primitiveClass, sb, start);
                genCtx.lastNullChecks = preservedNullChecks;
            }
            return;
        }
        if (!castET.isNumeric()) {
            return;
        }
        final String inner = sb.substring(start);
        // A primitive cast fails on a null instead.
        final boolean innerNonNull = genCtx.lastNonNull || LALCodegenHelper.PRIMITIVE_CASTS.contains(castType);
        sb.setLength(start);
        sb.append(wrapNumeric(inner, castET, innerNonNull, genCtx));
    }

    /**
     * Converts an object to a number through the type's runtime helper ({@code h.toInt(raw)}) and records the result
     * as that primitive; unless the object is never null, its null check lets a comparison order a missing value
     * first.
     */
    private static String wrapNumeric(final String raw, final ExprType type, final boolean rawNonNull,
                                      final LALClassGenerator.GenCtx genCtx) {
        if (!type.isNumeric()) {
            throw new IllegalStateException(type + " is not a numeric type");
        }
        final String converted = type.runtimeHelper + "(" + raw + ")";
        genCtx.lastResolvedType = type.primitiveClass;
        genCtx.lastRawChain = converted;
        genCtx.lastNullChecks = rawNonNull ? null : raw + " == null";
        genCtx.lastNullCheckOnObject = !rawNonNull;
        return converted;
    }

    static void generateConditionValue(final StringBuilder sb,
                                        final LALScriptModel.ConditionValue cv,
                                        final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.StringConditionValue) {
            sb.append('"')
              .append(DslJavaSourceText.toLiteral(
                  ((LALScriptModel.StringConditionValue) cv).getValue()))
              .append('"');
        } else if (cv instanceof LALScriptModel.NumberConditionValue) {
            // The literal's own type: 1.5 stays 1.5.
            sb.append(LALLiterals.NumericLiteral.parse(((LALScriptModel.NumberConditionValue) cv).getLiteral()).boxed());
        } else if (cv instanceof LALScriptModel.NullConditionValue) {
            sb.append("null");
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            final LALScriptModel.ValueAccessConditionValue vacv = (LALScriptModel.ValueAccessConditionValue) cv;
            generateBoxedValue(sb, vacv.getValue(), vacv.getCastType(), genCtx);
        } else {
            throw new IllegalStateException("Unhandled condition value " + cv.getClass().getSimpleName());
        }
    }

    /**
     * Renders a numeric comparison's right side as a primitive of the comparison's type: a number literal written in
     * that type ({@code 10000L} against a long), a value as {@link #renderAsPrimitive} renders it.
     */
    static void generateConditionValueNumeric(
            final StringBuilder sb,
            final LALScriptModel.ConditionValue cv,
            final ExprType target,
            final LALClassGenerator.GenCtx genCtx) {
        if (cv instanceof LALScriptModel.NumberConditionValue) {
            sb.append(LALLiterals.NumericLiteral.parse(((LALScriptModel.NumberConditionValue) cv).getLiteral())
                .as(target));
        } else if (cv instanceof LALScriptModel.ValueAccessConditionValue) {
            final LALScriptModel.ValueAccessConditionValue vacv =
                (LALScriptModel.ValueAccessConditionValue) cv;
            sb.append(renderAsPrimitive(vacv.getValue(), vacv.getCastType(), target, genCtx));
        } else {
            throw new IllegalArgumentException(
                "A " + cv.getClass().getSimpleName() + " cannot be compared with <, >, <= or >=");
        }
    }

    /**
     * Renders an operand as a primitive of the target type: a primitive one ({@link #emitOperandRespectingCast})
     * widened to it, an object converted by its helper, its null check recorded.
     */
    private static String renderAsPrimitive(
            final LALScriptModel.ValueAccess value,
            final String cast,
            final ExprType targetType,
            final LALClassGenerator.GenCtx genCtx) {
        final StringBuilder buf = new StringBuilder();
        emitOperandRespectingCast(buf, value, cast, genCtx);
        final Class<?> resolved = genCtx.lastResolvedType;
        if (isNumericPrimitiveClass(resolved) && genCtx.lastRawChain != null) {
            return widenPrimitiveExpr(genCtx.lastRawChain,
                exprTypeFromClass(resolved), targetType);
        }
        genCtx.lastNullChecks = genCtx.lastNonNull ? null : buf + " == null";
        genCtx.lastNullCheckOnObject = genCtx.lastNullChecks != null;
        return targetType.runtimeHelper + "(" + buf + ")";
    }

    private static boolean isNumericPrimitiveClass(final Class<?> c) {
        return c == int.class || c == long.class
            || c == float.class || c == double.class;
    }

    // ==================== Value access ====================

    /**
     * Generates a value access with an explicit type cast: a numeric cast leaves the primitive, with its form and
     * null check recorded for a comparison or arithmetic; a String or Boolean one an object; a class cast (only a
     * {@code def} takes one) the value itself.
     *
     * <p>LAL: {@code parsed.latency as Long}
     * <br>Generated: {@code h.toLong(h.mapVal("latency"))}
     */
    static void generateCastedValueAccess(final StringBuilder sb,
                                           final LALScriptModel.ValueAccess value,
                                           final String castType,
                                           final LALClassGenerator.GenCtx genCtx) {
        final ExprType castET = castToType(castType);
        if (castET.runtimeHelper == null) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        // Skip h.toStr(<string-literal>) — it's a no-op.
        if (castET == ExprType.STRING && isPlainStringLiteral(value)) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        if (castET == ExprType.STRING) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
            genCtx.clearExtraLogResult();
            genCtx.lastResolvedType = String.class;
            return;
        }
        final StringBuilder raw = new StringBuilder();
        generateValueAccess(raw, value, genCtx);
        rejectUnconvertibleCast(value, castET, genCtx);
        final boolean rawNonNull = genCtx.lastNonNull || LALCodegenHelper.PRIMITIVE_CASTS.contains(castType);
        if (castET.isNumeric()) {
            sb.append(wrapNumeric(raw.toString(), castET, rawNonNull, genCtx));
        } else {
            sb.append(castET.runtimeHelper).append("(").append(raw).append(")");
        }
    }

    private static void recordPrimitiveResult(final LALClassGenerator.GenCtx genCtx,
                                                final Class<?> type,
                                                final StringBuilder sb,
                                                final int start) {
        genCtx.lastResolvedType = type;
        genCtx.lastRawChain = sb.substring(start);
        genCtx.lastNullChecks = null;
        genCtx.lastNullCheckOnObject = false;
    }

    /**
     * Generates a value access that must produce a String result (used by
     * tag assignments). For numeric / boolean casts wraps the conversion
     * helper with {@code String.valueOf(...)}; for String (or unspecified)
     * wraps with {@code h.toStr(...)} — except when the value is already
     * a Java {@code String} literal, in which case the wrapper is elided
     * since {@code h.toStr("llm")} just returns {@code "llm"}.
     */
    static void generateStringValueAccess(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final String castType,
                                            final LALClassGenerator.GenCtx genCtx) {
        final ExprType castET = castToType(castType);
        if (castET.isNumeric() || castET == ExprType.BOOLEAN) {
            // Converted, then stringified: a null stays null, so a tag of it is not added.
            sb.append("h.toStr(");
            generateBoxedValue(sb, value, castType, genCtx);
            sb.append(")");
            return;
        }
        if (isPlainStringLiteral(value)) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        sb.append("h.toStr(");
        generateValueAccess(sb, value, genCtx);
        sb.append(")");
    }

    /**
     * Generates a value access that produces an Object (boxed) result.
     * Only applies String cast; other casts pass through as-is. Skips
     * the {@code h.toStr()} wrapper when the value is already a Java
     * String literal.
     */
    static void generateValueAccessObj(final StringBuilder sb,
                                        final LALScriptModel.ValueAccess value,
                                        final String castType,
                                        final LALClassGenerator.GenCtx genCtx) {
        if ("String".equals(castType) && !isPlainStringLiteral(value)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    /**
     * True when the operand is a bare Java String literal (no chain,
     * no concat, no paren wrapping). For these the value-access codegen
     * already emits a {@code String}, so a {@code h.toStr()} wrapper
     * adds nothing.
     */
    private static boolean isPlainStringLiteral(final LALScriptModel.ValueAccess value) {
        return value.isStringLiteral() && value.isBareLiteral();
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
            if (genCtx.inputType != null
                    && !LALCodegenHelper.LOGDATA_BUILDER_CLASS
                        .isAssignableFrom(genCtx.inputType)) {
                throw new IllegalArgumentException(
                    "tag() reads LogData tags but the input type is "
                        + genCtx.inputType.getName()
                        + ". Use a json{}/yaml{}/text{} parser, or access "
                        + "typed fields via parsed.* instead.");
            }
            LALChainCodegen.generateTypedChain(sb,
                "h.tagValue(\"" + DslJavaSourceText.toLiteral(stringLiteralArg(value, "tag", "KEY")) + "\")",
                String.class, false, value.getChain(), genCtx);
            // A missing tag reads as "".
            genCtx.lastNonNull |= value.getChain().isEmpty();
            return;
        }

        // sourceAttribute("KEY") — reads from LogMetadata.sourceAttributes (non-persistent).
        // Available regardless of input type (reads from metadata, not input).
        if ("sourceAttribute".equals(value.getFunctionCallName())) {
            LALChainCodegen.generateTypedChain(sb,
                "h.sourceAttributeValue(\""
                    + DslJavaSourceText.toLiteral(stringLiteralArg(value, "sourceAttribute", "os.name")) + "\")",
                String.class, false, value.getChain(), genCtx);
            genCtx.lastNonNull |= value.getChain().isEmpty();
            return;
        }

        // toJson(...) / toJsonArray(...)
        if (value.getFunctionCallName() != null) {
            generateBuiltinCall(sb, value, genCtx);
            return;
        }

        // Handle string/number literals
        if (value.isStringLiteral()) {
            LALChainCodegen.generateTypedChain(sb,
                "\"" + DslJavaSourceText.toLiteral(value.getSegments().get(0)) + "\"",
                String.class, false, value.getChain(), genCtx);
            genCtx.lastNonNull |= value.getChain().isEmpty();
            return;
        }
        if (value.isNumberLiteral()) {
            final LALLiterals.NumericLiteral lit = LALLiterals.NumericLiteral.parse(value.getSegments().get(0));
            LALChainCodegen.generateTypedChain(sb, lit.boxed(), lit.boxedClass(), false, value.getChain(), genCtx);
            genCtx.lastNonNull |= value.getChain().isEmpty();
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
            generateLogAccess(sb, chain, genCtx);
            return;
        }

        // Handle parsed.X.Y with compile-time type analysis
        if (value.isParsedRef()) {
            generateParsedAccess(sb, chain, genCtx);
            return;
        }

        final String primaryName = value.getSegments().get(0);
        final LALClassGenerator.LocalVarInfo localVar = genCtx.localVars.get(primaryName);
        if (localVar != null) {
            LALDefCodegen.generateDefVarChain(sb, localVar, chain, genCtx);
            return;
        }

        // true / false / null: the grammar's literal primaries, which no identifier can be named.
        if (LALScriptModel.ValueAccess.LITERAL_NAMES.contains(primaryName)) {
            if (!chain.isEmpty()) {
                throw new IllegalArgumentException(
                    "Cannot call a method on the literal " + primaryName + ": " + value.toPathString());
            }
            sb.append("null".equals(primaryName) ? "null" : "Boolean." + primaryName.toUpperCase());
            genCtx.lastNonNull = !"null".equals(primaryName);
            genCtx.lastResolvedType = "null".equals(primaryName) ? null : Boolean.class;
            return;
        }

        if (genCtx.allDefNames.contains(primaryName)) {
            throw new IllegalArgumentException(
                "def '" + primaryName + "' is used outside the block that declares it: a def is visible only "
                    + "in its own if branch, extractor or filter level, after its declaration");
        }
        throw new IllegalArgumentException(
            "Unknown name '" + primaryName + "' in '" + value.toPathString() + "': "
                + "expected parsed, log, a def variable, tag(...), sourceAttribute(...), "
                + "toJson(...), toJsonArray(...) or ProcessRegistry");
    }

    /**
     * The single string-literal argument of {@code tag(...)} / {@code sourceAttribute(...)}.
     */
    private static String stringLiteralArg(final LALScriptModel.ValueAccess value,
                                           final String function, final String example) {
        final List<LALScriptModel.FunctionArg> args = value.getFunctionCallArgs();
        if (args.size() != 1 || args.get(0).getCastType() != null
                || !args.get(0).getValue().isStringLiteral()
                || !args.get(0).getValue().getChain().isEmpty()) {
            throw new IllegalArgumentException(
                function + "() requires exactly one string literal argument, "
                    + "e.g. " + function + "(\"" + example + "\")");
        }
        return args.get(0).getValue().getSegments().get(0);
    }

    /**
     * {@code toJson(expr)} / {@code toJsonArray(expr)} and the chain after it.
     */
    private static void generateBuiltinCall(final StringBuilder sb,
                                            final LALScriptModel.ValueAccess value,
                                            final LALClassGenerator.GenCtx genCtx) {
        final String funcName = value.getFunctionCallName();
        final BuiltinFunction function = BuiltinFunction.named(funcName);
        if (function == null) {
            throw new IllegalArgumentException(
                "Unknown function '" + funcName + "(...)': expected tag, sourceAttribute, "
                    + String.join(", ", BuiltinFunction.names()));
        }
        final int argCount = value.getFunctionCallArgs().size();
        if (argCount != 1) {
            throw new IllegalArgumentException(
                funcName + "() requires exactly 1 argument, got " + argCount);
        }
        final LALScriptModel.FunctionArg arg = value.getFunctionCallArgs().get(0);
        final StringBuilder call = new StringBuilder();
        call.append(function.helper).append("(");
        generateBoxedValue(call, arg.getValue(), arg.getCastType(), genCtx);
        call.append(")");
        LALChainCodegen.generateTypedChain(sb, call.toString(), function.returnType, true,
            value.getChain(), genCtx);
    }

    /**
     * The functions a value may call besides {@code tag(...)} and {@code sourceAttribute(...)}.
     */
    enum BuiltinFunction {
        TO_JSON("toJson", "h.toJsonObject", JsonObject.class),
        TO_JSON_ARRAY("toJsonArray", "h.toJsonArray", JsonArray.class);

        final String dslName;
        /** The runtime helper it calls. */
        final String helper;
        final Class<?> returnType;

        BuiltinFunction(final String dslName, final String helper, final Class<?> returnType) {
            this.dslName = dslName;
            this.helper = helper;
            this.returnType = returnType;
        }

        static BuiltinFunction named(final String name) {
            for (final BuiltinFunction function : values()) {
                if (function.dslName.equals(name)) {
                    return function;
                }
            }
            return null;
        }

        static List<String> names() {
            final List<String> names = new ArrayList<>();
            for (final BuiltinFunction function : values()) {
                names.add(function.dslName);
            }
            return names;
        }
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
        final String castType = value.getParenCast();
        final ExprType castET = castToType(castType);
        if (value.getChain().isEmpty() && (castET.isNumeric() || castET == ExprType.BOOLEAN)) {
            // As an object the cast keeps a null, as Groovy's `null as Integer` does; arithmetic and comparisons
            // take the primitive form, guarded by the null check.
            final StringBuilder raw = new StringBuilder();
            generateValueAccess(raw, value.getParenInner(), genCtx);
            rejectUnconvertibleCast(value.getParenInner(), castET, genCtx);
            final boolean rawNonNull = genCtx.lastNonNull || LALCodegenHelper.PRIMITIVE_CASTS.contains(castType);
            sb.append(castObject(castType, raw.toString(), false));
            final boolean rawPure = genCtx.lastPure;
            genCtx.clearExtraLogResult();
            genCtx.lastNonNull = rawNonNull;
            genCtx.lastPure = rawPure;
            if (castET.isNumeric()) {
                wrapNumeric(raw.toString(), castET, rawNonNull, genCtx);
            } else {
                genCtx.lastResolvedType = Boolean.class;
            }
            return;
        }
        if (value.getChain().isEmpty()) {
            if (castType != null) {
                generateCastedValueAccess(sb, value.getParenInner(), castType, genCtx);
            } else {
                generateValueAccess(sb, value.getParenInner(), genCtx);
            }
            return;
        }
        // The inner value, cast, then its chain on the cast's type: a numeric cast as its box, keeping a null.
        final StringBuilder inner = new StringBuilder();
        final Class<?> innerType;
        if (castET.isNumeric() || castET == ExprType.BOOLEAN) {
            generateBoxedValue(inner, value.getParenInner(), castType, genCtx);
            innerType = castET.valueClass;
        } else if (castType != null) {
            generateCastedValueAccess(inner, value.getParenInner(), castType, genCtx);
            innerType = castET == ExprType.STRING ? String.class : Object.class;
        } else {
            generateValueAccess(inner, value.getParenInner(), genCtx);
            if ((genCtx.lastResolvedType == null || genCtx.lastResolvedType == Object.class)
                    && isTextGroupOfABranch(value.getParenInner(), genCtx)
                    && LALChainCodegen.callsAStringMethod(value.getChain())) {
                // As parsed.<group>.<method> takes it: a string, failing the cast for another branch's value.
                inner.insert(0, "(String) ");
                innerType = String.class;
            } else {
                innerType = genCtx.lastResolvedType == null ? Object.class
                    : DslMethods.boxed(genCtx.lastResolvedType);
            }
        }
        final boolean typedResult = genCtx.lastTypedResult && innerType == Object.class;
        // Parenthesized, so the chain applies to the whole value: ("api-" + x).toUpperCase().
        LALChainCodegen.generateTypedChain(sb, "(" + inner + ")", innerType, true, value.getChain(), genCtx,
            typedResult);
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
                                   final List<LALScriptModel.ValueAccessSegment> chain,
                                   final LALClassGenerator.GenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append("h.logView()");
            genCtx.lastResolvedType = LogData.class;
            return;
        }
        final LALScriptModel.ValueAccessSegment first = chain.get(0);
        String field = null;
        if (first instanceof LALScriptModel.FieldSegment) {
            field = ((LALScriptModel.FieldSegment) first).getName();
            if (!LALCodegenHelper.METADATA_GETTERS.containsKey(field)
                    && !LALCodegenHelper.LOG_GETTERS.containsKey(field)) {
                throw new IllegalArgumentException(
                    "Unknown log field: log." + field
                        + ". Supported metadata fields: "
                        + LALCodegenHelper.METADATA_GETTERS.keySet()
                        + ", LogData fields: "
                        + LALCodegenHelper.LOG_GETTERS.keySet());
            }
        } else if (first instanceof LALScriptModel.MethodSegment
                && ((LALScriptModel.MethodSegment) first).getArguments().isEmpty()) {
            // log.getService() reads what log.service does.
            final String method = ((LALScriptModel.MethodSegment) first).getName();
            for (final Map.Entry<String, String> getter : LALCodegenHelper.METADATA_GETTERS.entrySet()) {
                if (getter.getValue().equals(method)) {
                    field = getter.getKey();
                }
            }
            if ("getTags".equals(method) || "getTagsOrBuilder".equals(method)) {
                field = "tags";
            } else if ("getTraceContextOrBuilder".equals(method)) {
                field = "traceContext";
            } else if ("hasTags".equals(method) || "hasTraceContext".equals(method)) {
                // Present as the value getters read them, with the rule's writes.
                final String presence = "h.log" + Character.toUpperCase(method.charAt(0)) + method.substring(1) + "()";
                genCtx.lastResolvedType = boolean.class;
                genCtx.lastRawChain = presence;
                genCtx.lastNonNull = true;
                genCtx.lastPure = true;
                if (chain.size() > 1) {
                    LALChainCodegen.generateTypedChain(sb, presence, boolean.class, false, chain.subList(1, chain.size()),
                        genCtx);
                } else {
                    sb.append("Boolean.valueOf(").append(presence).append(")");
                }
                return;
            }
        } else if (first instanceof LALScriptModel.IndexSegment) {
            throw new IllegalArgumentException(
                "Index access on log.* is not supported: log" + describeChain(chain));
        }
        if (first instanceof LALScriptModel.MethodSegment && isLogMutation(((LALScriptModel.MethodSegment) first).getName())) {
            throw new IllegalArgumentException(
                "log." + ((LALScriptModel.MethodSegment) first).getName() + "(...) is not supported in a LAL rule: the "
                    + "extractor's statements set the log (service, instance, endpoint, layer, traceId, segmentId, "
                    + "spanId, timestamp, tag)");
        }

        final String rootExpr;
        final Class<?> rootType;
        final List<LALScriptModel.ValueAccessSegment> rest;
        if ("traceContext".equals(field) && first instanceof LALScriptModel.MethodSegment) {
            // log.getTraceContext() read the log's protobuf, which the rule's trace statements now write.
            rootExpr = "h.logTraceContext()";
            rootType = TraceContext.class;
            rest = chain.subList(1, chain.size());
        } else if ("tags".equals(field)) {
            // With the tags the rule's tag statements added.
            rootExpr = "h.logTags()";
            rootType = LogTags.class;
            rest = chain.subList(1, chain.size());
        } else if (field != null && LALCodegenHelper.METADATA_GETTERS.containsKey(field)) {
            // The standard fields are the rule's metadata, which its standard field statements set.
            final String getter = LALCodegenHelper.METADATA_GETTERS.get(field);
            rootExpr = "h.ctx().metadata()." + getter + "()";
            rootType = returnTypeOf(LogMetadata.class, getter);
            rest = chain.subList(1, chain.size());
        } else if (field != null) {
            final String getter = LALCodegenHelper.LOG_GETTERS.get(field);
            rootExpr = LOGDATA_BUILDER_CAST + "." + getter + "()";
            rootType = returnTypeOf(LALCodegenHelper.LOGDATA_BUILDER_CLASS, getter);
            rest = chain.subList(1, chain.size());
        } else {
            // Any other getter reads the log as the rule left it (getServiceBytes(), toString()).
            rootExpr = "h.logView()";
            rootType = LogData.class;
            rest = chain;
        }
        if (rest.isEmpty() && rootType.isPrimitive()) {
            genCtx.lastResolvedType = rootType;
            genCtx.lastRawChain = rootExpr;
            genCtx.lastNonNull = true;
            genCtx.lastPure = true;
            sb.append(LALCodegenHelper.box(rootType, rootExpr));
            return;
        }
        LALChainCodegen.generateTypedChain(sb, rootExpr, rootType, true, rest, genCtx);
        genCtx.lastPure = chain.stream().allMatch(seg -> seg instanceof LALScriptModel.FieldSegment);
    }

    /**
     * A log builder method that changes the log, which a rule does through its statements.
     */
    private static boolean isLogMutation(final String name) {
        for (final String prefix : new String[] {"set", "clear", "merge", "add", "remove", "put"}) {
            if (name.length() > prefix.length() && name.startsWith(prefix)
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> returnTypeOf(final Class<?> type, final String getter) {
        try {
            return DslMethods.find(type, getter).getReturnType();
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + " has no " + getter + "()", e);
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
     * json/yaml  parsed.client_process.address    h.prop(h.mapVal("client_process"), "address")
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
        final List<LALScriptModel.ValueAccessSegment> rest = methodStart >= 0
            ? chain.subList(methodStart, chain.size()) : List.of();

        String current;
        // A chain starting with a method calls it on the parsed holder itself: parsed.getMap().
        Class<?> currentType = fieldSegments.isEmpty() ? ExecutionContext.Parsed.class : Object.class;
        if (genCtx.parserType == LALClassGenerator.ParserType.NONE && genCtx.inputType == null) {
            // No parser in the rule: the log's own fields, read where the expression is, so a standard field
            // statement before is seen.
            LALChainCodegen.generateTypedChain(sb, "h.ctx().metadata()", LogMetadata.class, false, chain, genCtx);
            genCtx.lastPure = chain.stream().allMatch(seg -> seg instanceof LALScriptModel.FieldSegment);
            return;
        }
        final LALClassGenerator.ParserType parser = genCtx.scopeParser == LALClassGenerator.ParserType.NONE
            && genCtx.inputType == null ? LALClassGenerator.ParserType.JSON : genCtx.scopeParser;
        switch (parser) {
            case JSON:
            case YAML:
            case MIXED:
                current = "h.ctx().parsed()";
                for (int i = 0; i < fieldSegments.size(); i++) {
                    final String key = DslJavaSourceText.toLiteral(fieldSegments.get(i).getName());
                    // A `.` on a null fails as Groovy's does; only `?.` reads null.
                    current = i == 0 ? "h.mapVal(\"" + key + "\")"
                        : "h." + (fieldSegments.get(i).isSafeNav() ? "safeProp" : "prop")
                            + "(" + current + ", \"" + key + "\")";
                }
                if (parser == LALClassGenerator.ParserType.MIXED && fieldSegments.size() == 1
                        && LALChainCodegen.callsAStringMethod(rest)) {
                    // A text parser's group in one branch: a string there, as in a rule with only the text parser;
                    // another branch's non-string value fails the cast.
                    current = "((String) " + current + ")";
                    currentType = String.class;
                }
                break;
            case TEXT:
                if (fieldSegments.isEmpty()) {
                    current = "h.ctx().parsed()";
                    currentType = ExecutionContext.Parsed.class;
                    break;
                }
                // the first name reads a named group of the regexp, a string; the rest of the chain is that string's
                // (parsed.level.toString, parsed.level.trim(), and a name a string has not fails there)
                emitTail(sb, "h.group(\"" + DslJavaSourceText.toLiteral(fieldSegments.get(0).getName()) + "\")",
                    String.class, chain.subList(1, chain.size()), genCtx);
                return;
            case NONE:
                // A typed input, read through its getters as far as each name is one; the rest of the chain is the
                // value's (parsed.response.toString).
                final int getters = getterPrefix(fieldSegments, genCtx.inputType);
                current = generateExtraLogAccess(fieldSegments.subList(0, getters), genCtx.inputType, "_p", true,
                    genCtx);
                if (getters == 0) {
                    currentType = genCtx.inputType;
                } else if (genCtx.lastResolvedType != null) {
                    currentType = DslMethods.boxed(genCtx.lastResolvedType);
                }
                if (getters < fieldSegments.size()) {
                    emitTail(sb, current, currentType, chain.subList(getters, chain.size()), genCtx);
                    return;
                }
                break;
            default:
                throw new IllegalStateException("Unhandled parser type " + parser);
        }

        if (!rest.isEmpty()) {
            LALChainCodegen.generateTypedChain(sb, current, currentType, true, rest, genCtx);
            return;
        }
        if (currentType == String.class) {
            genCtx.lastResolvedType = String.class;
        }
        genCtx.lastPure = true;
        sb.append(current);
    }

    /**
     * The value a parsed root resolved to, followed by the rest of its chain, walked as any typed value's.
     */
    private static void emitTail(final StringBuilder sb, final String value, final Class<?> type,
                                 final List<LALScriptModel.ValueAccessSegment> tail,
                                 final LALClassGenerator.GenCtx genCtx) {
        if (tail.isEmpty()) {
            genCtx.lastResolvedType = type;
            genCtx.lastPure = true;
            sb.append(value);
            return;
        }
        LALChainCodegen.generateTypedChain(sb, value, type, true, tail, genCtx);
    }

    /**
     * @return how many of the leading names read a getter ({@code getX()}) of the type each one before returns
     */
    private static int getterPrefix(final List<LALScriptModel.FieldSegment> names, final Class<?> rootType) {
        Class<?> type = rootType;
        int count = 0;
        for (final LALScriptModel.FieldSegment name : names) {
            try {
                type = DslMethods.find(type, "get" + Character.toUpperCase(name.getName().charAt(0))
                    + name.getName().substring(1)).getReturnType();
            } catch (final NoSuchMethodException e) {
                break;
            }
            count++;
            if (type.isPrimitive()) {
                break;
            }
        }
        return count;
    }

    /**
     * Whether a value is {@code parsed.<name>} after a text and a json / yaml branch rejoin: the text branch's group,
     * a string there.
     */
    static boolean isTextGroupOfABranch(final LALScriptModel.ValueAccess value,
                                        final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess grouped = value.groupedInner();
        if (grouped != null) {
            return isTextGroupOfABranch(grouped, genCtx);
        }
        if (value.isBareIdentifier() && value.getChain().isEmpty()
                && genCtx.localVars.containsKey(value.getSegments().get(0))) {
            return genCtx.localVars.get(value.getSegments().get(0)).textGroup;
        }
        return genCtx.scopeParser == LALClassGenerator.ParserType.MIXED && value.isParsedRef()
            && value.getChain().size() == 1 && value.getChain().get(0) instanceof LALScriptModel.FieldSegment;
    }

    /**
     * Generates the {@code getX()} chain of {@code parsed.*} on a typed input ({@code parsed?.response?.responseCode}),
     * each getter resolved by reflection and allowed by {@link LALCallables}.
     *
     * <p>Safe navigation ({@code ?.}) generates null checks. Intermediate results
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
            final String getterName = "get" + Character.toUpperCase(field.charAt(0))
                + field.substring(1);

            final Method getter;
            try {
                getter = LALCallables.get().require(currentType, DslMethods.find(currentType, getterName));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "Cannot resolve getter " + currentType.getSimpleName()
                        + "." + getterName + "() for type "
                        + typeName + ". Check the field path in the LAL rule. " + LALCallables.get().describe(currentType));
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
                if (seg.isSafeNav() && prevCanBeNull) {
                    genCtx.lastNullChecks = prevVar + " == null";
                    return "(" + prevVar + " == null ? null : " + LALCodegenHelper.box(returnType, rawAccess) + ")";
                } else {
                    genCtx.lastNullChecks = null;
                    genCtx.lastNonNull = true;
                    return LALCodegenHelper.box(returnType, rawAccess);
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
        if (chain.isEmpty() || !(chain.get(0) instanceof LALScriptModel.MethodSegment)) {
            throw new IllegalArgumentException(
                "ProcessRegistry" + describeChain(chain) + ": call one of its methods, "
                    + "e.g. ProcessRegistry.generateVirtualLocalProcess(service, instance)");
        }
        final LALScriptModel.MethodSegment ms = (LALScriptModel.MethodSegment) chain.get(0);
        final Method method = findStaticMethod(ms.getName(), ms.getArguments().size());
        final StringBuilder call = new StringBuilder();
        call.append(PROCESS_REGISTRY).append(".").append(ms.getName()).append("(");
        final List<LALScriptModel.FunctionArg> args = ms.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            generateCastedValueAccess(call,
                args.get(i).getValue(), args.get(i).getCastType(), genCtx);
        }
        call.append(")");
        LALChainCodegen.generateTypedChain(sb, call.toString(), method.getReturnType(), false,
            chain.subList(1, chain.size()), genCtx);
    }

    private static Method findStaticMethod(final String name, final int argCount) {
        for (final Method m : ProcessRegistry.class.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount
                    && Modifier.isStatic(m.getModifiers())) {
                LALCallables.get().requireStatic(ProcessRegistry.class, name);
                return m;
            }
        }
        throw new IllegalArgumentException(
            "ProcessRegistry has no method " + name + "() taking " + argCount + " argument(s); the static calls are "
                + LALCallables.get().describeStatics() + " (see " + LALCallables.DOCUMENTATION + ")");
    }

    /**
     * The chain as written after its primary, e.g. {@code .a?.b()[0]}, for error messages.
     */
    static String describeChain(final List<LALScriptModel.ValueAccessSegment> chain) {
        final StringBuilder sb = new StringBuilder();
        for (final LALScriptModel.ValueAccessSegment seg : chain) {
            if (seg instanceof LALScriptModel.FieldSegment) {
                final LALScriptModel.FieldSegment fs = (LALScriptModel.FieldSegment) seg;
                sb.append(fs.isSafeNav() ? "?." : ".").append(fs.getName());
            } else if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms = (LALScriptModel.MethodSegment) seg;
                sb.append(ms.isSafeNav() ? "?." : ".").append(ms.getName()).append("(...)");
            } else {
                sb.append("[").append(((LALScriptModel.IndexSegment) seg).getIndex()).append("]");
            }
        }
        return sb.toString();
    }

    // ==================== Binary expression codegen ====================

    /**
     * JLS-style numeric type tag used for binary numeric promotion. Ordered narrow → wide so {@link #promote} can
     * pick the wider of two. A type a value can be converted to carries the {@code LalRuntimeHelper} method that
     * converts an arbitrary object to it.
     */
    enum ExprType {
        INT("int", "Integer", int.class, Integer.class, "h.toInt"),
        LONG("long", "Long", long.class, Long.class, "h.toLong"),
        FLOAT("float", "Float", float.class, Float.class, "h.toFloat"),
        DOUBLE("double", "Double", double.class, Double.class, "h.toDouble"),
        STRING(null, null, null, String.class, "h.toStr"),
        BOOLEAN("boolean", "Boolean", boolean.class, Boolean.class, "h.toBool"),
        /** A quotient of whole numbers, Groovy's BigDecimal, and arithmetic of it with whole numbers. */
        DECIMAL(null, null, null, BigDecimal.class, null),
        OBJECT(null, null, null, Object.class, null),
        UNKNOWN(null, null, null, null, null);

        final String primitiveKeyword;
        final String wrapperName;
        final Class<?> primitiveClass;
        /** The class a value of the type is, as an object; null when unknown. */
        final Class<?> valueClass;
        final String runtimeHelper;

        ExprType(final String primitiveKeyword, final String wrapperName, final Class<?> primitiveClass,
                 final Class<?> valueClass, final String runtimeHelper) {
            this.primitiveKeyword = primitiveKeyword;
            this.wrapperName = wrapperName;
            this.primitiveClass = primitiveClass;
            this.valueClass = valueClass;
            this.runtimeHelper = runtimeHelper;
        }

        boolean isNumeric() {
            return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
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
        // Groovy computes a float, even with another float, in double.
        if (a == ExprType.FLOAT || b == ExprType.FLOAT) {
            return ExprType.DOUBLE;
        }
        if (a == ExprType.LONG || b == ExprType.LONG) {
            return ExprType.LONG;
        }
        return ExprType.INT;
    }

    /**
     * @return whether the operand is known to be a boolean: a true/false literal, grouped or not, or a grouped value's
     *     chain ending in a boolean ({@code (tag("a")).isEmpty()})
     */
    private static boolean isBooleanOperand(final LALScriptModel.ValueAccess part,
                                            final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess grouped = part.groupedInner();
        if (grouped != null) {
            return isBooleanOperand(grouped, genCtx);
        }
        if (part.getParenInner() != null) {
            return part.getChain().isEmpty() ? castToType(part.getParenCast()) == ExprType.BOOLEAN
                : inferType(part, genCtx) == ExprType.BOOLEAN;
        }
        // the literal true/false, never a quoted "true" (a string) of that spelling
        return part.isBareLiteral() && !part.isStringLiteral()
            && ("true".equals(part.getSegments().get(0)) || "false".equals(part.getSegments().get(0)));
    }

    /**
     * The type of an operand as arithmetic, a comparison and string joining take it: its static type; a
     * {@code log.getX()} call is typed only to order it against a text ({@link #isKnownNumber}).
     */
    static ExprType inferType(final LALScriptModel.ValueAccess part,
                              final LALClassGenerator.GenCtx genCtx) {
        if (part == null || part.isLogRef() && !part.getChain().isEmpty()
                && !(part.getChain().get(0) instanceof LALScriptModel.FieldSegment)) {
            return ExprType.UNKNOWN;
        }
        final Class<?> type = staticType(part, genCtx);
        return type == null ? ExprType.UNKNOWN : exprTypeFromClass(type);
    }

    /**
     * The class an operand's value has when compiled, as the codegen renders it, or null when it is known only at
     * run time. It operates on the same AST the codegen subsequently emits, so the two agree.
     */
    private static Class<?> staticType(final LALScriptModel.ValueAccess part,
                                       final LALClassGenerator.GenCtx genCtx) {
        final String function = part.getFunctionCallName();
        if (part.isStringLiteral() || "tag".equals(function) || "sourceAttribute".equals(function)) {
            return LALChainCodegen.chainType(String.class, part.getChain(), genCtx);
        }
        if (part.isNumberLiteral()) {
            return LALChainCodegen.chainType(
                LALLiterals.NumericLiteral.parse(part.getSegments().get(0)).boxedClass(), part.getChain(), genCtx);
        }
        final BuiltinFunction builtin = BuiltinFunction.named(function);
        if (builtin != null) {
            return LALChainCodegen.chainType(builtin.returnType, part.getChain(), genCtx);
        }
        if (part.getParenInner() != null) {
            if (part.getChain().isEmpty()) {
                // A grouping only keeps the precedence: typed as what it holds, as an operand.
                return part.getParenCast() != null ? classOf(castToType(part.getParenCast()))
                    : inferType(part.getParenInner(), genCtx).valueClass;
            }
            // A grouped value's chain, (sourceAttribute("a")).length(): on a text, a boolean or a number the group
            // holds.
            final Class<?> inner = classOf(part.getParenCast() != null ? castToType(part.getParenCast())
                : inferType(part.getParenInner(), genCtx));
            return inner == null ? null : LALChainCodegen.chainType(inner, part.getChain(), genCtx);
        }
        if (part.isLogRef()) {
            return logType(part.getChain(), genCtx);
        }
        if (!part.getConcatParts().isEmpty()) {
            return inferBinaryType(part.getConcatParts(), part.getConcatOps(), genCtx).valueClass;
        }
        if (part.isBareIdentifier()) {
            final LALClassGenerator.LocalVarInfo def = genCtx.localVars.get(part.getSegments().get(0));
            if (def != null) {
                return LALChainCodegen.chainType(def.resolvedType, part.getChain(), genCtx, def.typedResult);
            }
        }
        if (part.isParsedRef() && genCtx.parserType == LALClassGenerator.ParserType.NONE
                && genCtx.inputType != null) {
            return typedInputType(part.getChain(), genCtx.inputType);
        }
        return null;
    }

    /**
     * The static type of {@code log.*}: a standard field and the chain on it, as the metadata or the log holds the
     * field (but its trace context and tags, which read as the log's protobuf), or a lone {@code log.getX()} of a
     * standard field but the trace context; null for anything else.
     */
    private static Class<?> logType(final List<LALScriptModel.ValueAccessSegment> chain,
                                    final LALClassGenerator.GenCtx genCtx) {
        if (chain.isEmpty()) {
            return null;
        }
        final LALScriptModel.ValueAccessSegment first = chain.get(0);
        String field = null;
        if (first instanceof LALScriptModel.FieldSegment) {
            field = ((LALScriptModel.FieldSegment) first).getName();
        } else if (chain.size() == 1 && first instanceof LALScriptModel.MethodSegment
                && ((LALScriptModel.MethodSegment) first).getArguments().isEmpty()) {
            for (final Map.Entry<String, String> getter : LALCodegenHelper.METADATA_GETTERS.entrySet()) {
                if (getter.getValue().equals(((LALScriptModel.MethodSegment) first).getName())) {
                    field = getter.getKey();
                }
            }
        }
        if (field == null || "traceContext".equals(field) || "tags".equals(field)) {
            return null;
        }
        final String metadataGetter = LALCodegenHelper.METADATA_GETTERS.get(field);
        final Class<?> root = metadataGetter != null ? returnTypeOf(LogMetadata.class, metadataGetter)
            : LALCodegenHelper.LOG_GETTERS.containsKey(field)
                ? returnTypeOf(LALCodegenHelper.LOGDATA_BUILDER_CLASS, LALCodegenHelper.LOG_GETTERS.get(field))
                : null;
        return root == null ? null : LALChainCodegen.chainType(root, chain.subList(1, chain.size()), genCtx);
    }

    /**
     * The primitive number or boolean a typed input's {@code getX()} chain ends at ({@code parsed.response.code}),
     * as {@link #generateExtraLogAccess} reads it; null for any other chain, whose type is known only at run time.
     */
    private static Class<?> typedInputType(final List<LALScriptModel.ValueAccessSegment> chain,
                                           final Class<?> inputType) {
        if (chain.isEmpty()) {
            return null;
        }
        Class<?> currentType = inputType;
        for (final LALScriptModel.ValueAccessSegment segment : chain) {
            if (!(segment instanceof LALScriptModel.FieldSegment)) {
                return null;
            }
            final String field = ((LALScriptModel.FieldSegment) segment).getName();
            try {
                currentType = DslMethods.find(currentType,
                    "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1)).getReturnType();
            } catch (final NoSuchMethodException e) {
                return null;
            }
        }
        final ExprType type = exprTypeFromClass(currentType);
        return currentType.isPrimitive() && (type.isNumeric() || type == ExprType.BOOLEAN) ? currentType : null;
    }

    /**
     * @return the class a value of a type holds (a number boxed), for a chain to resolve on; null for a run-time one
     */
    static Class<?> classOf(final ExprType type) {
        return type.isNumeric() || type == ExprType.STRING || type == ExprType.BOOLEAN ? type.valueClass : null;
    }

    /**
     * Map a Java {@link Class} (primitive or wrapper) to the matching {@link ExprType}.
     */
    private static ExprType exprTypeFromClass(final Class<?> c) {
        if (c == int.class || c == Integer.class) {
            return ExprType.INT;
        }
        if (c == long.class || c == Long.class) {
            return ExprType.LONG;
        }
        if (c == float.class || c == Float.class) {
            return ExprType.FLOAT;
        }
        if (c == double.class || c == Double.class) {
            return ExprType.DOUBLE;
        }
        if (c == String.class) {
            return ExprType.STRING;
        }
        if (c == boolean.class || c == Boolean.class) {
            return ExprType.BOOLEAN;
        }
        if (c == BigDecimal.class) {
            return ExprType.DECIMAL;
        }
        return ExprType.OBJECT;
    }

    static ExprType castToType(final String cast) {
        if ("Integer".equals(cast) || "int".equals(cast)) {
            return ExprType.INT;
        }
        if ("Long".equals(cast) || "long".equals(cast)) {
            return ExprType.LONG;
        }
        if ("Float".equals(cast) || "float".equals(cast)) {
            return ExprType.FLOAT;
        }
        if ("Double".equals(cast) || "double".equals(cast)) {
            return ExprType.DOUBLE;
        }
        if ("String".equals(cast)) {
            return ExprType.STRING;
        }
        if ("Boolean".equals(cast) || "boolean".equals(cast)) {
            return ExprType.BOOLEAN;
        }
        return ExprType.UNKNOWN;
    }

    /**
     * A primitive cast's conversion as an object: never null, as a null fails it (a boolean's is false).
     */
    private static String primitiveCastObject(final ExprType castET, final String raw) {
        return castET.wrapperName + ".valueOf(" + castET.runtimeHelper + "(" + raw + "))";
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
            if (op == LALScriptModel.BinaryOp.PLUS && (acc == ExprType.STRING || rhs == ExprType.STRING)) {
                acc = ExprType.STRING;
                continue;
            }
            if (isWholeOrDecimal(acc) && isWholeOrDecimal(rhs)
                    && (op == LALScriptModel.BinaryOp.SLASH || acc == ExprType.DECIMAL || rhs == ExprType.DECIMAL)) {
                acc = ExprType.DECIMAL;
                continue;
            }
            if ((acc.isNumeric() || acc == ExprType.DECIMAL) && (rhs.isNumeric() || rhs == ExprType.DECIMAL)) {
                // A decimal with a float or double computes in double, as Groovy's.
                acc = acc == ExprType.DECIMAL || rhs == ExprType.DECIMAL ? ExprType.DOUBLE : promote(acc, rhs);
                continue;
            }
            // A `+` on a value whose type is known only at run time: Groovy's plus decides then.
            if (op == LALScriptModel.BinaryOp.PLUS) {
                acc = ExprType.OBJECT;
                continue;
            }
            // - * / on non-numeric — caller emits a compile error.
            return ExprType.UNKNOWN;
        }
        return acc;
    }

    /**
     * Top-level binary-expression codegen. Mirrors Java's left-to-right
     * evaluation: adjacent numeric operands accumulate into a primitive
     * arithmetic expression; once a String operand appears, every subsequent
     * {@code +} becomes string concatenation. This preserves the semantic
     * difference between {@code 1 + 2 + "x"} (= {@code "3x"}) and
     * {@code "" + 1 + 2 + "x"} (= {@code "12x"}).
     */
    private static void generateBinaryExpression(
            final StringBuilder sb,
            final List<LALScriptModel.ValueAccess> parts,
            final List<LALScriptModel.BinaryOp> ops,
            final LALClassGenerator.GenCtx genCtx) {
        // Render the first operand and start the accumulator with its type.
        ExprType accType = inferType(parts.get(0), genCtx);
        final StringBuilder accBuf = new StringBuilder();
        appendOperandRaw(accBuf, parts.get(0), accType, genCtx);
        String accExpr = accBuf.toString();
        // The first operand alone, while nothing has been combined with it: a nullable number that a
        // concatenation or a run-time plus takes as an object, so its null reads "null" as in Groovy.
        LALScriptModel.ValueAccess accOperand = genCtx.lastNullChecks != null ? parts.get(0) : null;

        for (int i = 1; i < parts.size(); i++) {
            final LALScriptModel.BinaryOp op = ops.get(i - 1);
            final ExprType rhsType = inferType(parts.get(i), genCtx);
            final StringBuilder rhsBuf = new StringBuilder();
            appendOperandRaw(rhsBuf, parts.get(i), rhsType, genCtx);
            String rhsExpr = rhsBuf.toString();
            final boolean rhsNullable = genCtx.lastNullChecks != null;

            if (isWholeOrDecimal(accType) && isWholeOrDecimal(rhsType)
                    && (op == LALScriptModel.BinaryOp.SLASH || accType == ExprType.DECIMAL
                        || rhsType == ExprType.DECIMAL)) {
                // Groovy divides whole numbers to a BigDecimal (7 / 2 is 3.5), and keeps one in arithmetic with
                // whole numbers: h.quotient and h.decimal compute it as Groovy's BigDecimalMath does.
                accExpr = op == LALScriptModel.BinaryOp.SLASH && isIntegral(accType) && isIntegral(rhsType)
                    ? "h.quotient((long) (" + accExpr + "), (long) (" + rhsExpr + "))"
                    : "h.decimal(" + boxed(accExpr, accType) + ", \"" + op.symbol() + "\", "
                        + boxed(rhsExpr, rhsType) + ")";
                accType = ExprType.DECIMAL;
                accOperand = null;
                continue;
            }
            if (accType == ExprType.DECIMAL && rhsType.isNumeric() || accType.isNumeric() && rhsType == ExprType.DECIMAL) {
                // With a float or double, in double, as Groovy's.
                accExpr = "(" + asDouble(accExpr, accType) + " " + op.symbol() + " " + asDouble(rhsExpr, rhsType) + ")";
                accType = ExprType.DOUBLE;
                accOperand = null;
                continue;
            }
            if (accType.isNumeric() && rhsType.isNumeric()) {
                final ExprType promoted = promote(accType, rhsType);
                final String widenedAcc = widenPrimitiveExpr(accExpr, accType, promoted);
                final String widenedRhs = widenPrimitiveExpr(rhsExpr, rhsType, promoted);
                accExpr = "(" + widenedAcc + " " + op.symbol() + " " + widenedRhs + ")";
                accType = promoted;
                accOperand = null;
                continue;
            }
            if (op == LALScriptModel.BinaryOp.STAR && (accType == ExprType.STRING || rhsType == ExprType.STRING)) {
                throw new IllegalArgumentException(
                    "A string cannot be multiplied: Groovy repeated it ('b' * 2 is 'bb'), LAL does not. Build the "
                        + "repeated string with + instead, as LAL has no loop");
            }
            if (op != LALScriptModel.BinaryOp.PLUS) {
                throw new IllegalArgumentException(
                    "Operator '" + op.symbol() + "' requires numeric operands; "
                        + "got non-numeric expression. Cast operands with "
                        + "'as Integer/Long/Float/Double' to enable arithmetic.");
            }
            final boolean accBoolean = accType == ExprType.BOOLEAN || i == 1 && isBooleanOperand(parts.get(0), genCtx);
            final boolean rhsBoolean = rhsType == ExprType.BOOLEAN || isBooleanOperand(parts.get(i), genCtx);
            if (accBoolean || rhsBoolean && accType != ExprType.STRING && accType != ExprType.OBJECT
                    && accType != ExprType.UNKNOWN) {
                // Groovy has no plus on a boolean, nor a number's plus taking one; only a string joins one
                throw new IllegalArgumentException("A boolean takes no part in '+': only a string joins one "
                    + "(\"x\" + true)");
            }
            if (i == 1 && rhsType == ExprType.STRING) {
                // a text joins only onto a text, a number, a character or null; a value of another known type is
                // refused now, one typed only at run time when the rule runs (h.plus)
                final Class<?> leftType = staticType(parts.get(0), genCtx);
                if (!DslNumbers.mayJoinText(leftType)) {
                    throw DslNumbers.cannotJoinText(leftType);
                }
            }
            final boolean joinsText = accType == ExprType.STRING || rhsType == ExprType.STRING;
            final Class<?> rhsStatic = joinsText ? staticType(parts.get(i), genCtx) : null;
            if (DslNumbers.isItems(rhsStatic)) {
                // an array, a collection or a map is no text, on either side of a text +
                throw DslNumbers.cannotJoinText(rhsStatic);
            }
            final boolean accIsObject = accOperand != null || !accType.isNumeric();
            if (accOperand != null) {
                accExpr = objectForm(accOperand, genCtx);
            }
            final boolean rhsIsObject = rhsNullable || !rhsType.isNumeric();
            if (rhsNullable && rhsType.isNumeric()) {
                rhsExpr = objectForm(parts.get(i), genCtx);
            }
            accOperand = null;
            if (rhsType == ExprType.STRING && (accType == ExprType.OBJECT || accType == ExprType.UNKNOWN)) {
                // a value typed only at run time beside a string: h.plus refuses a boolean there, else joins
                accExpr = "((String) h.plus(" + accExpr + ", " + rhsExpr + "))";
                accType = ExprType.STRING;
            } else if (accType == ExprType.STRING || rhsType == ExprType.STRING) {
                // Java concatenates as Groovy's String#plus does; a grouped operand stays grouped (5 + (1 + "ms")).
                final LALScriptModel.ValueAccess rhsPart = parts.get(i);
                final boolean grouped = rhsType == ExprType.STRING && (!rhsPart.getConcatParts().isEmpty()
                    || rhsPart.getParenInner() != null && rhsPart.getParenCast() == null
                        && !rhsPart.getParenInner().getConcatParts().isEmpty());
                // a value typed only at run time is checked then: an array, a collection or a map fails
                final String joined = rhsType == ExprType.OBJECT || rhsType == ExprType.UNKNOWN
                    ? "h.joined(" + rhsExpr + ")" : rhsExpr;
                accExpr = accExpr + " + " + (grouped ? "(" + joined + ")" : joined);
                accType = ExprType.STRING;
            } else {
                accExpr = "h.plus(" + (accIsObject ? accExpr : boxed(accExpr, accType)) + ", "
                    + (rhsIsObject ? rhsExpr : boxed(rhsExpr, rhsType)) + ")";
                accType = ExprType.OBJECT;
            }
        }

        genCtx.lastNullChecks = null;
        genCtx.lastNonNull = accType != ExprType.OBJECT;
        if (accType.isNumeric()) {
            // Expose the primitive form so the caller (numeric comparison)
            // can avoid h.toX() unboxing; emit a boxed form into sb for
            // Object contexts (tag assignments, def initialisers, ...).
            genCtx.lastResolvedType = accType.primitiveClass;
            genCtx.lastRawChain = accExpr;
            sb.append(accType.wrapperName).append(".valueOf(").append(accExpr).append(")");
        } else {
            // A String concatenation, or a run-time plus. Update the
            // genCtx metadata explicitly — otherwise a stale numeric
            // resolved type from an inner operand would leak out and
            // mis-type things like `def msg = "count=" + (tag("n") as Integer)`,
            // which the def codegen reads from lastResolvedType to declare
            // the local variable.
            genCtx.lastResolvedType = accType == ExprType.STRING ? String.class
                : accType == ExprType.DECIMAL ? BigDecimal.class : Object.class;
            genCtx.lastRawChain = null;
            sb.append(accExpr);
        }
    }

    private static boolean isIntegral(final ExprType type) {
        return type == ExprType.INT || type == ExprType.LONG;
    }

    private static boolean isWholeOrDecimal(final ExprType type) {
        return isIntegral(type) || type == ExprType.DECIMAL;
    }

    /**
     * A numeric operand, or a decimal one, as a double.
     */
    private static String asDouble(final String expr, final ExprType type) {
        return type == ExprType.DECIMAL ? "(" + expr + ").doubleValue()" : "(double) (" + expr + ")";
    }

    /**
     * An operand as an object: a nullable number stays null instead of failing its primitive conversion.
     */
    private static String objectForm(final LALScriptModel.ValueAccess operand,
                                     final LALClassGenerator.GenCtx genCtx) {
        final StringBuilder buf = new StringBuilder();
        generateValueAccess(buf, operand, genCtx);
        return buf.toString();
    }

    private static String boxed(final String expr, final ExprType type) {
        return type.isNumeric() ? type.wrapperName + ".valueOf(" + expr + ")" : expr;
    }

    /**
     * Render a single operand for use inside a binary expression — returning
     * its raw form (primitive expression for numeric operands, Object/String
     * form otherwise). Number literals emit with their declared suffix,
     * paren-cast operands emit through the matching helper, paren-grouping
     * recurses into the inner expression, and nested binary expressions
     * reuse the inner accumulator's raw chain when numeric.
     */
    private static void appendOperandRaw(final StringBuilder sb,
                                          final LALScriptModel.ValueAccess part,
                                          final ExprType partType,
                                          final LALClassGenerator.GenCtx genCtx) {
        genCtx.clearExtraLogResult();
        if (part.isNumberLiteral() && part.getChain().isEmpty()) {
            sb.append(LALLiterals.NumericLiteral.parse(part.getSegments().get(0)).javaText);
            return;
        }
        if (part.getParenInner() != null && part.getParenCast() != null
                && part.getChain().isEmpty() && partType.isNumeric()) {
            generateCastedValueAccess(sb, part.getParenInner(),
                partType.wrapperName, genCtx);
            return;
        }
        if (!part.getConcatParts().isEmpty()) {
            final StringBuilder inner = new StringBuilder();
            generateBinaryExpression(inner, part.getConcatParts(),
                part.getConcatOps(), genCtx);
            if (partType.isNumeric() && genCtx.lastRawChain != null) {
                sb.append(genCtx.lastRawChain);
            } else {
                sb.append(inner);
            }
            return;
        }
        if (partType.isNumeric()) {
            // The primitive form when the operand has one (a typed-proto ?. leaf's, whose null guard the caller
            // reads), else the object converted by h.toX, which fails on a null.
            final StringBuilder buf = new StringBuilder();
            generateValueAccess(buf, part, genCtx);
            if (genCtx.lastRawChain != null
                    && isNumericPrimitiveClass(genCtx.lastResolvedType)) {
                sb.append(widenPrimitiveExpr(genCtx.lastRawChain,
                    exprTypeFromClass(genCtx.lastResolvedType), partType));
            } else {
                // A boxed number, such as a def's, may be null: a concatenation then takes it as an object.
                genCtx.lastNullChecks = genCtx.lastNonNull ? null : buf + " == null";
                genCtx.lastNullCheckOnObject = genCtx.lastNullChecks != null;
                sb.append(partType.runtimeHelper).append("(").append(buf).append(")");
            }
        } else {
            generateValueAccess(sb, part, genCtx);
        }
    }
}
