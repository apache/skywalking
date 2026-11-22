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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import com.google.gson.JsonElement;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslValues;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.BinaryOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.CompareOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.LogicalOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALBoolConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALComparisonCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALExprCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALLogicalCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNotCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNullConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNumberConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALStringConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;

/**
 * The expression half of TAL's code generation: value access, conditions, casts, arithmetic and the reflective
 * getter chains, emitted as Java source for Javassist to compile.
 *
 * <p>What a rule can start an expression with — {@code span}, {@code meta}, {@code attr()} — is supplied by
 * {@link TALRootResolver}. Everything below is independent of spans ({@code toStr}, {@code toInt} and
 * {@code toLong} are {@code TalValueConversions}'):
 *
 * <pre>{@code
 * Expression type      Example                             Generated code
 * ──────────────────── ─────────────────────────────────── ──────────────────────────────────────────────────
 * String literal       "SLOW_SQL"                          "SLOW_SQL"
 * Number literal       500                                 Integer.valueOf(500)
 * Input root chain     span.child?.name                    (_p.getChild() == null ? null : _p.getChild().getName())
 * def variable         myVar.getAsString()                 _def_myVar.getAsString()
 * Parenthesized        (expr as String).trim()             (toStr(...)).trim()
 * String concat        meta.service + ":" + meta.endpoint  h.ctx().getMeta().getService() + ":" + ...
 * Arithmetic (int)     (a as Integer) + (b as Integer)     Integer.valueOf((toInt(..) + toInt(..)))
 * Arithmetic (mixed)   (a as Integer) * (b as Long)        Long.valueOf(((long) toInt(..) * toLong(..)))
 * }</pre>
 *
 * <p>Condition codegen ({@link #generateCondition}) handles comparisons ({@code ==}, {@code !=}, {@code <},
 * {@code >}), logical operators, negation and expression-as-boolean.
 *
 * <p>Sibling implementation: {@code LALValueCodegen} in {@code log-analyzer} compiles the same expression grammar
 * for LAL; both compare, order and convert values through the shared helpers of
 * {@code org.apache.skywalking.oap.server.core.dsl}.
 */
public final class TALValueCodegen {
    private TALValueCodegen() {
        // utility class
    }

    // ==================== Conditions ====================

    /**
     * Generates code for an {@code if} condition expression: a comparison, a logical combination, a negation, or
     * a bare expression read for its truth.
     *
     * <p>A comparison is decided by the static types of its two operands, see {@link #generateComparison}. A bare
     * expression is true when it is present and not empty, or, for a boolean, when it is true.
     */
    public static void generateCondition(final StringBuilder sb,
                                   final TALCondition cond,
                                   final TALGenCtx genCtx) {
        if (cond instanceof TALComparisonCondition) {
            generateComparison(sb, (TALComparisonCondition) cond, genCtx);
        } else if (cond instanceof TALLogicalCondition) {
            final TALLogicalCondition lc =
                (TALLogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft(), genCtx);
            sb.append(lc.getOp() == LogicalOp.AND
                ? " && " : " || ");
            generateCondition(sb, lc.getRight(), genCtx);
            sb.append(")");
        } else if (cond instanceof TALNotCondition) {
            sb.append("!(");
            generateCondition(sb,
                ((TALNotCondition) cond).getInner(), genCtx);
            sb.append(")");
        } else if (cond instanceof TALExprCondition) {
            final String ct = ((TALExprCondition) cond).getCastType();
            final TALValueAccess expr = ((TALExprCondition) cond).getExpr();
            if (ct == null && (expr.isLiteral("true") || expr.isLiteral("false") || expr.isLiteral("null"))) {
                sb.append(expr.isLiteral("true") ? "true" : "false");
                return;
            }
            // A built-in that already returns boolean is the condition. Wrapping it would hand a primitive to
            // an Object overload, which Javassist compiles without autoboxing and the verifier then rejects.
            if (ct == null && expr.getFunctionCallName() != null && expr.getChain().isEmpty()
                    && genCtx.resolver.functionReturnType(expr, genCtx) == ExprType.BOOLEAN) {
                generateValueAccess(sb, expr, genCtx);
                return;
            }
            sb.append(TALRuntimeRoles.method("Boolean".equals(ct) ? "isTrue" : "isNotEmpty")).append("(");
            generateBoxedValueAccess(sb, expr, ct, genCtx);
            sb.append(")");
        } else {
            throw new IllegalArgumentException("Unsupported condition in TAL rule: " + cond);
        }
    }

    // ==================== Typed comparisons ====================

    /**
     * What a comparison operand is at compile time. A comparison is legal for a pair of kinds only, see
     * {@link #generateComparison}.
     */
    private enum OperandKind {
        NUMBER, BOOLEAN, STRING, OBJECT, RUNTIME, NULL
    }

    /**
     * One side of a comparison, rendered as a boxed Java expression.
     */
    private static final class Operand {
        private final String expr;
        private final OperandKind kind;
        /**
         * The static class for an {@code OBJECT}, so two objects compare only when they are of one type.
         */
        private final Class<?> type;
        /**
         * Whether the expression can be null, which a primitive read cannot.
         */
        private final boolean nullable;
        private final boolean stringLiteral;
        private final String source;

        private Operand(final String expr, final OperandKind kind, final Class<?> type, final boolean nullable,
                        final boolean stringLiteral, final String source) {
            this.expr = expr;
            this.kind = kind;
            this.type = type;
            this.nullable = nullable;
            this.stringLiteral = stringLiteral;
            this.source = source;
        }

        private String describe() {
            switch (kind) {
                case NUMBER:
                    return "a number";
                case BOOLEAN:
                    return "a boolean";
                case STRING:
                    return "a String";
                case OBJECT:
                    return "a " + type.getSimpleName();
                case NULL:
                    return "null";
                case RUNTIME:
                default:
                    return "a value whose type is known only at runtime";
            }
        }
    }

    /**
     * A comparison, checked by {@link #requireComparable} and emitted as a null check, a Layer or an enum value
     * against a string literal by name, or {@code compareOrdered}, which decides {@code ==} and {@code !=} by content
     * and orders the rest; a String equal to a string literal is {@code String.equals}, as compareOrdered decides it.
     *
     * <p>An {@code as T} operand keeps a null, which equals only a null and orders below every value, and a value it
     * cannot convert fails the span, as LAL compares.
     */
    private static void generateComparison(final StringBuilder sb,
                                           final TALComparisonCondition cc,
                                           final TALGenCtx genCtx) {
        final Operand left = operand(cc.getLeft(), cc.getLeftCast(), genCtx);
        final Operand right = rightOperand(cc.getRight(), genCtx);
        final CompareOp op = cc.getOp();
        requireComparable(left, op, right);
        final boolean equality = op == CompareOp.EQ || op == CompareOp.NEQ;
        if (equality && (left.kind == OperandKind.NULL || right.kind == OperandKind.NULL)) {
            final Operand other = left.kind == OperandKind.NULL ? right : left;
            sb.append("(").append(other.expr).append(op == CompareOp.EQ ? " == null)" : " != null)");
            return;
        }
        final Operand named = namedAgainstLiteral(left, right);
        if (named != null) {
            final Operand literal = named == left ? right : left;
            if (named.type.isEnum()) {
                // An enum has one instance per constant
                sb.append("(").append(named.expr).append(op == CompareOp.EQ ? " == " : " != ")
                  .append(named.type.getName()).append(".").append(literalName(literal)).append(")");
                return;
            }
            sb.append(TALRuntimeRoles.method("sameName")).append("(").append(named.expr).append(", ")
              .append(literal.expr).append(", ").append(op == CompareOp.EQ).append(")");
            return;
        }
        if (equality && left.kind == OperandKind.STRING && right.kind == OperandKind.STRING
            && (left.stringLiteral || right.stringLiteral)) {
            // String.equals, which compareOrdered decides alike, with the literal first, as it is never null
            final Operand literal = right.stringLiteral ? right : left;
            final Operand other = literal == right ? left : right;
            sb.append("(").append(op == CompareOp.EQ ? "" : "!").append(literal.expr).append(".equals(")
              .append(other.expr).append("))");
            return;
        }
        sb.append(TALRuntimeRoles.method("compareOrdered")).append("(").append(left.expr).append(", ")
          .append(right.expr).append(", ").append(op.ordinal()).append(")");
    }

    /**
     * Refuses a comparison the static types of its two operands do not allow, as LAL compares.
     *
     * <ul>
     *   <li>number and number: any operator;</li>
     *   <li>a string, a character or a value of unknown type against a number: any operator, as the number the text
     *       reads as; a string literal ordered against a number has to read as one;</li>
     *   <li>two strings or values of unknown type ordered: as numbers when both read as one, else as text;</li>
     *   <li>boolean and boolean, String and String, two objects of one type, a JSON value against anything but an
     *       object of another type, and a value of unknown type against anything but an object: {@code ==} and
     *       {@code !=}; arrays, collections and maps against each other or a value of unknown type alike;</li>
     *   <li>a value that can be absent and {@code null}: {@code ==} and {@code !=}; anything orders above a null;</li>
     *   <li>a Layer or an enum value and a string literal naming one of its values: {@code ==} and {@code !=};</li>
     *   <li>anything else: a compile error naming both types.</li>
     * </ul>
     */
    private static void requireComparable(final Operand left, final CompareOp op, final Operand right) {
        final boolean equality = op == CompareOp.EQ || op == CompareOp.NEQ;
        if (left.kind == OperandKind.NULL || right.kind == OperandKind.NULL) {
            final Operand other = left.kind == OperandKind.NULL ? right : left;
            if (equality && (other.kind == OperandKind.NULL || !other.nullable)) {
                throw incomparable(left, op, right, "only a value that can be absent compares with null, by == or !=");
            }
            return;
        }
        final Operand named = namedAgainstLiteral(left, right);
        if (named != null) {
            // A Layer or an enum value compares with a string literal by name; the literal is checked now, so a
            // misspelt name is a compile error where it is written.
            if (!equality) {
                throw incomparable(left, op, right, "a Layer or an enum value compares by name, with == or !=");
            }
            final String name = literalName(named == left ? right : left);
            TALDefCodegen.requireTypedName(named.type, name, incomparable(left, op, right,
                "'" + name + "' is no " + named.type.getSimpleName() + " name").getMessage());
            return;
        }
        final Operand json = isJson(left.type) ? left : isJson(right.type) ? right : null;
        if (equality && json != null) {
            final Operand other = json == left ? right : left;
            if (other.kind != OperandKind.OBJECT || holdsItems(other.type)) {
                // a JSON value compares as what it holds: a primitive as its number, text or boolean
                return;
            }
        }
        if (left.kind == OperandKind.NUMBER && right.kind == OperandKind.NUMBER) {
            return;
        }
        if (isOrderable(left) && isOrderable(right)
            && (!equality || left.kind == OperandKind.NUMBER || right.kind == OperandKind.NUMBER)) {
            // A string against a number reads as a number, and two strings ordered compare as numbers when both
            // read as one, else as text, as LAL compares them; an ordering literal that is no number fails now.
            if (!equality) {
                requireNumberText(left, right, op);
            }
            return;
        }
        if (equality && (left.kind == OperandKind.RUNTIME || right.kind == OperandKind.RUNTIME)
            && left.kind != OperandKind.OBJECT && right.kind != OperandKind.OBJECT) {
            // Two values whose types are known only when the rule runs: equal by value, as LAL compares them.
            return;
        }
        if (equality && (left.kind == OperandKind.RUNTIME && holdsItems(right.type)
            || right.kind == OperandKind.RUNTIME && holdsItems(left.type))) {
            // an array, a collection or a map against a value of unknown type, by content
            return;
        }
        final boolean sameKind = left.kind == right.kind
            && (left.kind == OperandKind.BOOLEAN || left.kind == OperandKind.STRING
                || left.kind == OperandKind.OBJECT && (left.type.equals(right.type)
                    || holdsItems(left.type) && holdsItems(right.type)));
        if (!sameKind) {
            throw incomparable(left, op, right, "convert one side with `as T`");
        }
        if (!equality) {
            throw incomparable(left, op, right, "only numbers are ordered; convert with `as T`");
        }
    }

    /**
     * @return the side of the comparison that is a Layer or an enum value against a string literal, else null
     */
    private static Operand namedAgainstLiteral(final Operand left, final Operand right) {
        return isNamedValue(left) && right.stringLiteral ? left : isNamedValue(right) && left.stringLiteral ? right
            : null;
    }

    private static String literalName(final Operand literal) {
        return literal.source.substring(1, literal.source.length() - 1);
    }

    private static boolean isFloatingType(final Class<?> type) {
        final Class<?> boxed = type == null ? null : DslMethods.boxed(type);
        return boxed == Double.class || boxed == Float.class;
    }

    private static boolean isOrderable(final Operand operand) {
        return operand.kind == OperandKind.NUMBER || operand.kind == OperandKind.STRING
            || operand.kind == OperandKind.RUNTIME;
    }

    /**
     * A string literal ordered against a number has to read as one.
     */
    private static void requireNumberText(final Operand left, final Operand right, final CompareOp op) {
        for (final Operand literal : new Operand[] {left, right}) {
            final Operand other = literal == left ? right : left;
            if (literal.stringLiteral && other.kind == OperandKind.NUMBER) {
                final String text = literal.source.substring(1, literal.source.length() - 1);
                if (DslNumbers.numberIn(text) == null) {
                    throw incomparable(left, op, right, "'" + text + "' is no number to order against one");
                }
            }
        }
    }

    private static boolean isNamedValue(final Operand operand) {
        return operand.kind == OperandKind.OBJECT && (operand.type == Layer.class || operand.type.isEnum());
    }

    private static IllegalArgumentException incomparable(final Operand left, final CompareOp op,
                                                         final Operand right, final String hint) {
        return new IllegalArgumentException(
            "cannot compare " + left.describe() + " with " + right.describe() + " in `" + left.source + " "
                + symbolOf(op) + " " + right.source + "`: " + hint);
    }

    private static String symbolOf(final CompareOp op) {
        switch (op) {
            case EQ:
                return "==";
            case NEQ:
                return "!=";
            case GT:
                return ">";
            case LT:
                return "<";
            case GTE:
                return ">=";
            case LTE:
            default:
                return "<=";
        }
    }

    private static Operand rightOperand(final TALConditionValue cv, final TALGenCtx genCtx) {
        if (cv instanceof TALStringConditionValue) {
            final String text = ((TALStringConditionValue) cv).getValue();
            return new Operand("\"" + DslJavaSourceText.toLiteral(text) + "\"", OperandKind.STRING, String.class,
                true, true, "\"" + text + "\"");
        }
        if (cv instanceof TALNumberConditionValue) {
            final String literal = ((TALNumberConditionValue) cv).getLiteral();
            final TALLiterals.NumericLiteral parsed = TALLiterals.NumericLiteral.parse(literal);
            return new Operand(parsed.type.wrapperName + ".valueOf(" + parsed.javaText + ")", OperandKind.NUMBER,
                parsed.type.primitiveClass, false, false, literal);
        }
        if (cv instanceof TALBoolConditionValue) {
            final boolean value = ((TALBoolConditionValue) cv).isValue();
            return new Operand(value ? "Boolean.TRUE" : "Boolean.FALSE", OperandKind.BOOLEAN, boolean.class,
                false, false, String.valueOf(value));
        }
        if (cv instanceof TALNullConditionValue) {
            return new Operand("null", OperandKind.NULL, null, true, false, "null");
        }
        final TALValueAccessConditionValue vacv = (TALValueAccessConditionValue) cv;
        return operand(vacv.getValue(), vacv.getCastType(), genCtx);
    }

    /**
     * Render one operand boxed and classify it. An {@code as T} converts through a helper that keeps a null and fails
     * the span on a value it cannot convert.
     */
    private static Operand operand(final TALValueAccess value, final String cast, final TALGenCtx genCtx) {
        final String path = value.isStringLiteral() ? "\"" + value.toPathString() + "\"" : value.toPathString();
        final String source = path + (cast == null ? "" : " as " + cast);
        if (value.getParenInner() != null && value.getChain().isEmpty() && value.getConcatParts().isEmpty()
            && (cast == null || value.getParenCast() == null)) {
            // Grouping: `(x as Integer)`, `((x as Integer))` and `(x) as Integer` are the conversion `x as Integer`.
            return operand(value.getParenInner(), cast != null ? cast : value.getParenCast(), genCtx);
        }
        final Class<?> typed = TALDefCodegen.typedCastClass(cast);
        if (typed != null) {
            // A Layer or an enum value, its literal checked now; it then compares by name with a string literal and
            // with a value of its own type only. A name that is none fails the span, as LAL fails the log.
            final StringBuilder rendered = new StringBuilder();
            generateTypedConversion(rendered, value, typed, genCtx);
            return new Operand(rendered.toString(), OperandKind.OBJECT, typed, true, false, source);
        }
        if (ExprType.isScalarCast(cast)) {
            final StringBuilder converted = new StringBuilder();
            generateBoxedValueAccess(converted, value, cast, genCtx);
            // A cast keeps a null, so its result can be absent.
            return classified(converted.toString(), genCtx.lastResolvedType, true, false, source);
        }
        final boolean javaCast = cast != null && ExprType.ofCast(cast) == ExprType.UNKNOWN;
        final Class<?> castClass = javaCast ? TALDefCodegen.resolveDefCastType(cast) : null;
        if (value.isLiteral("null")) {
            return new Operand("null", OperandKind.NULL, null, true, false, source);
        }
        if (javaCast) {
            // A class name: the Java cast, which fails the span on a value of another class, as LAL fails the log.
            final StringBuilder boxed = new StringBuilder();
            generateBoxedValueAccess(boxed, value, null, genCtx);
            requireCastable(genCtx.lastResolvedType, castClass, "`as " + castClass.getName() + "`");
            genCtx.clearExtraLogResult();
            return classified("((" + TALCodegenHelper.javaTypeName(castClass) + ") " + boxed + ")", castClass, true,
                false, source);
        }
        final boolean asString = "String".equals(cast);
        final StringBuilder rendered = new StringBuilder();
        generateValueAccessObj(rendered, value, asString ? "String" : null, genCtx);
        final Class<?> type = asString ? String.class : genCtx.lastResolvedType;
        if (type != null && type.isPrimitive()) {
            final String box = TALCodegenHelper.boxTypeName(type);
            // A primitive read is boxed for the helpers; one read through a null-guarded chain is already boxed.
            final String expr = genCtx.lastRawChain != null && !genCtx.lastNullChecks && box != null
                ? box + ".valueOf(" + genCtx.lastRawChain + ")" : rendered.toString();
            return classified(expr, type, genCtx.lastNullChecks, false, source);
        }
        return classified(rendered.toString(), type, true, value.isStringLiteral() && cast == null, source);
    }

    /**
     * An operand of a static type: a character, the one-character string it is, and any other text as its String;
     * a value of unknown type, a number, a boolean, a String, else an object.
     */
    private static Operand classified(final String expr, final Class<?> type, final boolean nullable,
                                      final boolean stringLiteral, final String source) {
        if (type == null || type == Object.class) {
            return new Operand(expr, OperandKind.RUNTIME, null, nullable, stringLiteral, source);
        }
        if (type == char.class || type == Character.class
            || type != String.class && CharSequence.class.isAssignableFrom(type)) {
            return new Operand(TALRuntimeRoles.method("toStr") + "(" + expr + ")", OperandKind.STRING, String.class,
                nullable, false, source);
        }
        final Class<?> boxed = DslMethods.boxed(type);
        final OperandKind kind = Number.class.isAssignableFrom(boxed) ? OperandKind.NUMBER
            : boxed == Boolean.class ? OperandKind.BOOLEAN
            : type == String.class ? OperandKind.STRING
            : OperandKind.OBJECT;
        return new Operand(expr, kind, type, nullable, stringLiteral, source);
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
     * Wrap an Object-typed operand of arithmetic in the conversion of its numeric type.
     */
    private static String wrapAsPrimitive(final String objectExpr, final ExprType target) {
        final String helper = target.runtimeHelper != null && target.isNumeric()
            ? target.runtimeHelper : TALRuntimeRoles.method("toLong");
        return helper + "(" + objectExpr + ")";
    }

    private static boolean isNumericPrimitiveClass(final Class<?> c) {
        return c == int.class || c == long.class
            || c == float.class || c == double.class;
    }

    // ==================== Value access ====================

    /**
     * Generates a value access with an explicit type cast: a scalar type through its conversion, {@code Layer} and an
     * enum by name, a class by its name as a Java cast.
     *
     * <p>TAL: {@code attr("retries") as Long}
     * <br>Generated: {@code TalValueConversions.toLong(<the attr call>)}; {@code meta.latency as Long}, a long
     * already, is its read alone.
     */
    public static void generateCastedValueAccess(final StringBuilder sb,
                                           final TALValueAccess value,
                                           final String castType,
                                           final TALGenCtx genCtx) {
        final Class<?> typed = TALDefCodegen.typedCastClass(castType);
        if (typed != null) {
            generateTypedConversion(sb, value, typed, genCtx);
            return;
        }
        // Each numeric cast produces a primitive expression; record the type and the raw chain so callers (numeric
        // comparisons, arithmetic codegen) can skip a redundant conversion when the operand already matches the
        // target primitive.
        final ExprType castET = ExprType.ofCast(castType);
        if (castET.runtimeHelper == null && castType != null && castType.indexOf('.') > 0) {
            // A class name is a Java cast, so the value has that static type wherever it goes: the overload a call
            // or a setter chose for it by that type, see argumentTypes, is the one the compiled call takes.
            final Class<?> castClass = TALDefCodegen.resolveDefCastType(castType);
            final StringBuilder rendered = new StringBuilder();
            generateValueAccess(rendered, value, genCtx);
            requireCastable(genCtx.lastResolvedType, castClass, "`as " + castType + "`");
            final String raw = rawPrimitive(genCtx);
            sb.append("((").append(TALCodegenHelper.javaTypeName(castClass)).append(") (")
              .append(raw != null ? TALCodegenHelper.boxTypeName(genCtx.lastResolvedType) + ".valueOf(" + raw + ")"
                  : rendered)
              .append("))");
            genCtx.clearExtraLogResult();
            genCtx.lastResolvedType = castClass;
            return;
        }
        if (castET.runtimeHelper == null) {
            // No cast: the raw value access.
            generateValueAccess(sb, value, genCtx);
            return;
        }
        // A string literal is its own text.
        if (castET == ExprType.STRING && value.isStringLiteral()) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        final StringBuilder rendered = new StringBuilder();
        generateValueAccess(rendered, value, genCtx);
        final String raw = rawPrimitive(genCtx);
        final Class<?> source = genCtx.lastResolvedType;
        if (castET.isNumeric()) {
            requireNoCharacter(source, value, castET.wrapperName);
        }
        final int start = sb.length();
        final Class<?> target = castET == ExprType.BOOLEAN ? boolean.class : castET.primitiveClass;
        if (raw != null && source == target) {
            // Already that primitive: the raw read, with no conversion around it.
            sb.append(raw);
        } else {
            // The helpers' Object overloads take any boxed value, so a primitive is boxed rather than matched to
            // one of the few primitive overloads.
            sb.append(castET.runtimeHelper).append("(")
              .append(raw != null ? TALCodegenHelper.boxTypeName(source) + ".valueOf(" + raw + ")" : rendered)
              .append(")");
        }
        if (target != null) {
            recordPrimitiveResult(genCtx, target, sb, start);
        } else {
            genCtx.clearExtraLogResult();
            genCtx.lastResolvedType = String.class;
        }
    }

    /**
     * @return the raw primitive form of the value just generated, when it is a primitive read outside a
     * null-guarded chain; null otherwise. A primitive result always records its raw form, so a chain guarded by
     * {@code ?.} is the only primitive read without one, and it is already boxed.
     */
    private static String rawPrimitive(final TALGenCtx genCtx) {
        final Class<?> type = genCtx.lastResolvedType;
        if (type == null || !type.isPrimitive() || genCtx.lastRawChain == null || genCtx.lastNullChecks
            || TALCodegenHelper.boxTypeName(type) == null) {
            return null;
        }
        return genCtx.lastRawChain;
    }

    /**
     * {@code as Layer} and {@code as <Enum>} wherever a cast stands: a value of that type as it is, a string by
     * name, a literal name checked now.
     */
    static void generateTypedConversion(final StringBuilder sb, final TALValueAccess value, final Class<?> typed,
                                        final TALGenCtx genCtx) {
        TALDefCodegen.checkTypedLiteral(typed, value);
        final StringBuilder inner = new StringBuilder();
        generateBoxedValueAccess(inner, value, null, genCtx);
        if (genCtx.lastResolvedType == typed) {
            sb.append(inner);
        } else {
            sb.append(TALDefCodegen.typedCastConversion(typed, inner, genCtx));
        }
        genCtx.clearExtraLogResult();
        genCtx.lastResolvedType = typed;
    }

    /**
     * A value as an {@code Object}, for the places Java takes one, such as a log argument: an {@code as} converts
     * first, and a primitive result is boxed, since Javassist does not autobox.
     */
    public static void generateBoxedValueAccess(final StringBuilder sb,
                                                final TALValueAccess value,
                                                final String castType,
                                                final TALGenCtx genCtx) {
        if (ExprType.isScalarCast(castType) && !readsPrimitive(value, genCtx)) {
            // A value that can be null keeps it through the cast, as LAL casts; one that does not convert fails.
            final StringBuilder plain = new StringBuilder();
            generateValueAccess(plain, value, genCtx);
            requireNoCharacter(genCtx.lastResolvedType, value, castType);
            sb.append(TALRuntimeRoles.method("as" + castType)).append("(").append(plain).append(")");
            genCtx.clearExtraLogResult();
            genCtx.lastResolvedType = ExprType.ofCast(castType).boxedClass;
            return;
        }
        final StringBuilder rendered = new StringBuilder();
        if (castType != null) {
            generateCastedValueAccess(rendered, value, castType, genCtx);
        } else {
            generateValueAccess(rendered, value, genCtx);
        }
        final String raw = rawPrimitive(genCtx);
        if (raw == null) {
            sb.append(rendered);
            return;
        }
        final Class<?> type = genCtx.lastResolvedType;
        sb.append(TALCodegenHelper.boxTypeName(type)).append(".valueOf(").append(raw).append(")");
        genCtx.clearExtraLogResult();
        genCtx.lastResolvedType = DslMethods.boxed(type);
    }

    /**
     * A value passed where Java takes a {@code paramType}: a setter's parameter or a contributed function's.
     * A Layer or an enum parameter converts as {@code as} does, by name; a scalar one converts as {@code as} does,
     * after the rule's own {@code as} when it wrote one; a boxed scalar keeps a null; any other object is passed as
     * it is, cast to the parameter's type.
     *
     * @param what names the parameter in a compile error
     */
    public static void generateArgumentFor(final StringBuilder sb,
                                           final TALValueAccess value,
                                           final String castType,
                                           final Class<?> paramType,
                                           final String what,
                                           final TALGenCtx genCtx) {
        final Class<?> typed = TALDefCodegen.typedCastClass(castType);
        if (paramType == Layer.class || paramType.isEnum()) {
            if (castType != null && typed != paramType && !"String".equals(castType)) {
                throw new IllegalArgumentException(
                    what + " takes a " + paramType.getSimpleName() + ", not a value converted with as " + castType);
            }
            generateTypedConversion(sb, value, paramType, genCtx);
            return;
        }
        // The cast applies first; its value then passes where the parameter takes it, or as its name where the
        // parameter takes a String.
        if (typed != null && paramType != String.class && !paramType.isAssignableFrom(typed)) {
            throw new IllegalArgumentException(
                what + " takes a " + paramType.getSimpleName() + ", not a " + typed.getSimpleName());
        }
        final ExprType kind = scalarKind(paramType);
        if (paramType.isPrimitive() && kind == null) {
            throw new IllegalArgumentException(
                what + " takes a " + paramType.getName() + ", which TAL has no value of; take an int or a String");
        }
        if (kind == ExprType.STRING) {
            generateStringValueAccess(sb, value, castType, genCtx);
            return;
        }
        if (kind != null && paramType.isPrimitive()) {
            final String kindCast = kind == ExprType.BOOLEAN ? "Boolean" : kind.wrapperName;
            // A byte or short parameter takes the int conversion narrowed to it.
            final boolean narrow = paramType == byte.class || paramType == short.class;
            sb.append(narrow ? "((" + paramType.getName() + ") " : "");
            if (castType == null || ExprType.ofCast(castType) == kind) {
                generateCastedValueAccess(sb, value, kindCast, genCtx);
            } else {
                // The rule's conversion first, then the parameter's: `x as Double` into an int truncates.
                sb.append(kind.runtimeHelper).append("(");
                generateBoxedValueAccess(sb, value, castType, genCtx);
                sb.append(")");
            }
            sb.append(narrow ? ")" : "");
            return;
        }
        final StringBuilder boxed = new StringBuilder();
        generateBoxedValueAccess(boxed, value, castType, genCtx);
        final Class<?> type = genCtx.lastResolvedType;
        if (type != null && paramType.isAssignableFrom(type)) {
            sb.append(boxed);
        } else if (kind != null) {
            sb.append("(").append(paramType.getName()).append(") ").append(TALRuntimeRoles.method("toBoxed")).append("(")
              .append(paramType.getName())
              .append(".class, ").append(boxed).append(")");
        } else {
            requireCastable(type, paramType, what);
            sb.append("(").append(TALCodegenHelper.javaTypeName(paramType)).append(") ").append(boxed);
        }
    }

    /**
     * A Java cast that no value of the static type can pass would fail on every span, so it is a compile error.
     *
     * @param what the cast or the parameter, for the message
     */
    static void requireCastable(final Class<?> type, final Class<?> target, final String what) {
        if (type != null && !TALCodegenHelper.castable(DslMethods.boxed(type), target)) {
            // Only the scalar names `as` accepts make a hint: `as Short` does not parse.
            final boolean convertible = ExprType.ofCast(target.getSimpleName()).boxedClass == target;
            throw new IllegalArgumentException(
                what + " takes a " + TALCodegenHelper.javaTypeName(target) + ", and a "
                    + TALCodegenHelper.javaTypeName(DslMethods.boxed(type)) + " never is one"
                    + (convertible ? "; convert it with as " + target.getSimpleName() : ""));
        }
    }

    /**
     * The arguments of a Java method a chain calls, each converted to the parameter the method takes, as a
     * contributed function's are.
     *
     * @return the arguments as Java source, comma-separated
     */
    static String generateArgumentsFor(final List<? extends TALFunctionArg> args, final Method method,
                                       final TALGenCtx genCtx) {
        final Class<?>[] params = method.getParameterTypes();
        final Class<?>[] argTypes = chainArgumentTypes(args, genCtx);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final TALFunctionArg arg = args.get(i);
            final Class<?> param = params[i];
            final Class<?> unboxed = argTypes[i] == null ? null
                : argTypes[i].isPrimitive() ? argTypes[i] : DslMethods.unboxed(argTypes[i]);
            if (param.isPrimitive() && unboxed == char.class) {
                // A character passes to a char parameter, and widens to a number one as Java passes it, its code.
                final StringBuilder boxed = new StringBuilder();
                generateBoxedValueAccess(boxed, arg.getValue(), arg.getCastType(), genCtx);
                sb.append("((").append(param.getName()).append(") ((java.lang.Character) ").append(boxed)
                  .append(").charValue())");
            } else if (!param.isPrimitive() && argTypes[i] == Object.class && param != Object.class) {
                // A value of a type known only when the rule runs is cast to the parameter, as LAL casts it.
                final StringBuilder boxed = new StringBuilder();
                generateBoxedValueAccess(boxed, arg.getValue(), arg.getCastType(), genCtx);
                sb.append("((").append(TALCodegenHelper.javaTypeName(param)).append(") ").append(boxed).append(")");
            } else {
                generateArgumentFor(sb, arg.getValue(), arg.getCastType(), param,
                    "argument " + (i + 1) + " of " + method.getName() + "()", genCtx);
            }
            genCtx.clearExtraLogResult();
        }
        return sb.toString();
    }

    /**
     * @return each argument's type as a chain's method resolution reads it: null for a literal null, {@code Object}
     * for a value whose type is known only when the rule runs
     */
    static Class<?>[] chainArgumentTypes(final List<? extends TALFunctionArg> args, final TALGenCtx genCtx) {
        if (args == null || args.isEmpty()) {
            return new Class<?>[0];
        }
        final Class<?>[] types = argumentTypes(args, genCtx);
        for (int i = 0; i < types.length; i++) {
            if (args.get(i).getCastType() == null && args.get(i).getValue().isLiteral("null")) {
                types[i] = null;
            } else if (types[i] == null) {
                types[i] = Object.class;
            }
        }
        return types;
    }

    /**
     * The compile-time type of each argument of a call, which chooses among same-arity overloads: the type an
     * {@code as} converts to, else the type the argument's own code resolves to, null when that is unknown.
     */
    static Class<?>[] argumentTypes(final List<? extends TALFunctionArg> args, final TALGenCtx genCtx) {
        final Class<?>[] types = new Class<?>[args.size()];
        for (int i = 0; i < types.length; i++) {
            final String cast = args.get(i).getCastType();
            final Class<?> typed = TALDefCodegen.typedCastClass(cast);
            final ExprType castType = ExprType.ofCast(cast);
            if (typed != null) {
                types[i] = typed;
            } else if (castType == ExprType.STRING) {
                types[i] = String.class;
            } else if (castType == ExprType.BOOLEAN) {
                types[i] = boolean.class;
            } else if (castType.primitiveClass != null) {
                types[i] = castType.primitiveClass;
            } else if (cast != null && cast.indexOf('.') > 0) {
                // A class name: the rule states the type the value is converted to.
                types[i] = TALDefCodegen.resolveDefCastType(cast);
            } else {
                types[i] = resolvedType(args.get(i).getValue(), genCtx);
            }
        }
        return types;
    }

    /**
     * @return the scalar kind a parameter type is, primitive or boxed, or null for any other type
     */
    private static ExprType scalarKind(final Class<?> type) {
        final ExprType kind = exprTypeFromClass(type);
        return kind == ExprType.OBJECT ? null : kind;
    }

    private static void recordPrimitiveResult(final TALGenCtx genCtx,
                                                final Class<?> type,
                                                final StringBuilder sb,
                                                final int start) {
        genCtx.lastResolvedType = type;
        genCtx.lastRawChain = sb.substring(start);
        genCtx.lastNullChecks = false;
    }

    /**
     * Generates a value access that must produce a String result (used by tag assignments). The rule's own cast
     * converts first, through the same emitter as anywhere else: a numeric or boolean cast is then stringified with
     * {@code String.valueOf(...)}, a Layer or an enum cast gives its name; any other value goes through
     * {@code toStr(...)}, boxed when it is a primitive, but for a string literal, which is its own text.
     */
    public static void generateStringValueAccess(final StringBuilder sb,
                                            final TALValueAccess value,
                                            final String castType,
                                            final TALGenCtx genCtx) {
        final ExprType castET = ExprType.ofCast(castType);
        if ((castET.isNumeric() || castET == ExprType.BOOLEAN) && !readsPrimitive(value, genCtx)) {
            // A null stays null through the cast, so the text is null rather than a failure.
            sb.append(TALRuntimeRoles.method("toStr")).append("(");
            generateBoxedValueAccess(sb, value, castType, genCtx);
            sb.append(")");
        } else if (castET.isNumeric() || castET == ExprType.BOOLEAN) {
            sb.append("String.valueOf(");
            generateCastedValueAccess(sb, value, castType, genCtx);
            sb.append(")");
        } else if ((castType == null || castET == ExprType.STRING) && value.isStringLiteral()) {
            generateValueAccess(sb, value, genCtx);
        } else {
            sb.append(TALRuntimeRoles.method("toStr")).append("(");
            generateBoxedValueAccess(sb, value, castType == null || castET == ExprType.STRING ? null : castType,
                genCtx);
            sb.append(")");
        }
        genCtx.clearExtraLogResult();
        genCtx.lastResolvedType = String.class;
    }

    /**
     * Generates a value access that produces an Object (boxed) result. Only applies String cast; other casts pass
     * through as-is. A string literal is its own text, which needs no {@code toStr(...)}.
     */
    public static void generateValueAccessObj(final StringBuilder sb,
                                        final TALValueAccess value,
                                        final String castType,
                                        final TALGenCtx genCtx) {
        if ("String".equals(castType) && !value.isStringLiteral()) {
            sb.append(TALRuntimeRoles.method("toStr")).append("(");
            generateBoxedValueAccess(sb, value, null, genCtx);
            sb.append(")");
            genCtx.clearExtraLogResult();
            genCtx.lastResolvedType = String.class;
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    /**
     * Central dispatch for all TAL value-access expressions. Routes each expression to the appropriate codegen path
     * based on its AST type.
     *
     * <p>Dispatch order:
     * <ol>
     *   <li>Binary expression ({@code a + b * c})</li>
     *   <li>Parenthesized expression ({@code (expr as Type).chain})</li>
     *   <li>Function call ({@code attr("k")}, {@code round(x)}), its chain resolved on what it returns</li>
     *   <li>String, boolean, null and number literals</li>
     *   <li>{@code meta.X.Y} and {@code span.X.Y} getter chains</li>
     *   <li>{@code def} variable reference with its chain</li>
     *   <li>Fallback: compile-time error</li>
     * </ol>
     */
    public static void generateValueAccess(final StringBuilder sb,
                                     final TALValueAccess value,
                                     final TALGenCtx genCtx) {
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

        // Built-in function calls are TAL's own vocabulary: attr(), resource(), component() and the rest.
        // The resolver claims the name or lets the generic paths below continue.
        if (value.getFunctionCallName() != null) {
            final StringBuilder call = new StringBuilder();
            if (genCtx.resolver.generateFunctionCall(call, value, genCtx)) {
                final ExprType returns = genCtx.resolver.functionReturnType(value, genCtx);
                // The arguments' code left its own metadata; what follows describes the call's result alone.
                genCtx.clearExtraLogResult();
                final boolean returnsString = returns == ExprType.STRING;
                if (value.getChain().isEmpty()) {
                    sb.append(call);
                    // The built-in's declared type, so a comparison or arithmetic around it is typed; a primitive
                    // result is left raw and its raw form recorded for the callers that box or widen it.
                    if (returnsString) {
                        genCtx.lastResolvedType = String.class;
                    } else if (returns != null && returns.primitiveClass != null) {
                        genCtx.lastResolvedType = returns.primitiveClass;
                        genCtx.lastRawChain = call.toString();
                    } else if (returns == ExprType.BOOLEAN) {
                        genCtx.lastResolvedType = boolean.class;
                        genCtx.lastRawChain = call.toString();
                    } else if (returns == ExprType.OBJECT) {
                        genCtx.lastResolvedType = genCtx.resolver.functionReturnClass(value, genCtx);
                        genCtx.lastResolvedElementType =
                            genCtx.resolver.functionReturnElementType(value, genCtx);
                        if (genCtx.lastResolvedType != null && genCtx.lastResolvedType.isPrimitive()) {
                            // A byte, short or char result: raw, for the callers that box it.
                            genCtx.lastRawChain = call.toString();
                        }
                    }
                    return;
                }
                // `attr("x").trim()`, `parseJsonObject(t).get("k")`, `round(x).toString()`: the methods after the
                // call are resolved on what it returns, a primitive as its box.
                final Class<?> returnClass = returnsString ? String.class
                    : returns == ExprType.OBJECT ? genCtx.resolver.functionReturnClass(value, genCtx)
                    : returns == ExprType.BOOLEAN ? boolean.class
                    : returns != null ? returns.primitiveClass : null;
                if (returnClass == null) {
                    throw new IllegalArgumentException(
                        "only a built-in that returns a value can be followed by a method or field; "
                            + value.getFunctionCallName() + "() does not");
                }
                TALDefCodegen.generateDefVarChain(sb, new TALDefCodegen.ChainReceiver(call.toString(), returnClass, null,
                    null, true), value.getChain(), genCtx);
                return;
            }
        }

        // Handle string/number literals
        if (value.isStringLiteral()) {
            sb.append("\"").append(DslJavaSourceText.toLiteral(value.getSegments().get(0)))
              .append("\"");
            genCtx.lastResolvedType = String.class;
            return;
        }
        if (value.isLiteral("null")) {
            sb.append("null");
            return;
        }
        if (value.isLiteral("true") || value.isLiteral("false")) {
            sb.append("Boolean.").append(value.bareName().toUpperCase());
            genCtx.lastResolvedType = Boolean.class;
            return;
        }
        if (value.isNumberLiteral()) {
            final String num = value.getSegments().get(0);
            final TALLiterals.NumericLiteral lit = TALLiterals.NumericLiteral.parse(num);
            // The literal's own type, so `def n = 1` declares a number and `n + 2` adds rather than concatenates.
            genCtx.lastResolvedType = lit.type.primitiveClass;
            genCtx.lastRawChain = lit.javaText;
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

        if (value.isStaticRef() && genCtx.resolver.generateStaticRefAccess(sb, value, genCtx)) {
            return;
        }

        final List<? extends TALValueAccessSegment> chain = value.getChain();

        if (value.isMetaRef()) {
            TALInputs.require(TALConstruct.META, genCtx.inputAlias);
            genCtx.resolver.generateMetaRootAccess(sb, chain, genCtx);
            return;
        }

        if (value.isInputRef()) {
            genCtx.resolver.generateInputRootAccess(sb, chain, genCtx);
            return;
        }

        // Check for def variable reference
        if (!value.getSegments().isEmpty()) {
            final String primaryName = value.getSegments().get(0);
            final TALLocalVar localVar =
                genCtx.localVars.get(primaryName);
            if (localVar != null) {
                TALDefCodegen.generateDefVarChain(sb, TALDefCodegen.ChainReceiver.of(localVar), chain, genCtx);
                return;
            }
        }

        // No matching codegen path — fail at compile time instead of silently generating null. A chain whose
        // first name is none of the roots is a misspelling such as `mta.kind`, not a field of the input, unless it is
        // the root of another input, which is reported as that input's.
        if (!value.getSegments().isEmpty() && "pairing".equals(value.getSegments().get(0))) {
            TALInputs.require(TALConstruct.PAIRING, genCtx.inputAlias);
        }
        final String desc = value.getFunctionCallName() != null
            ? "function call '" + value.getFunctionCallName() + "(...)'"
            : "'" + value.toPathString() + "'";
        throw new IllegalArgumentException(
            "Cannot resolve " + desc + ": an expression starts with span, meta, a def variable, a function or a "
                + "literal. " + genCtx.resolver.describeContext());
    }

    /**
     * Generates code for a parenthesized expression with optional type cast
     * and method chain.
     *
     * <p>TAL: {@code (attr("db.system.name") as String).trim()}
     * <br>Generated: {@code (TalValueConversions.toStr(<the attr call>)).trim()}
     */
    public static void generateParenAccess(final StringBuilder sb,
                                     final TALValueAccess value,
                                     final TALGenCtx genCtx) {
        // Generate the inner expression with cast
        final String castType = value.getParenCast();
        if (value.getChain().isEmpty() && ExprType.isScalarCast(castType)) {
            // `(x as Integer)` keeps a null as `x as Integer` does; arithmetic unboxes it on its own path.
            generateBoxedValueAccess(sb, value.getParenInner(), castType, genCtx);
            return;
        }
        final boolean safeFirst = !value.getChain().isEmpty() && isSafeNavigation(value.getChain().get(0));
        final StringBuilder inner = new StringBuilder();
        if (safeFirst && ExprType.isScalarCast(castType)) {
            // `(x as Long)?.toString()`: the cast keeps its null for the `?.` to read.
            generateBoxedValueAccess(inner, value.getParenInner(), castType, genCtx);
        } else if (castType != null) {
            generateCastedValueAccess(inner, value.getParenInner(), castType, genCtx);
        } else {
            generateValueAccess(inner, value.getParenInner(), genCtx);
        }

        final Class<?> innerType = genCtx.lastResolvedType;
        // `(m).put(...)` changes the map the grouped expression holds, when the rule made it.
        final TALValueAccess held = TALDefCodegen.keepsValue(castType) ? value.getParenInner() : null;
        if (safeFirst && innerType != null && !(innerType.isPrimitive() && rawPrimitive(genCtx) != null)) {
            heldChain(sb, inner.toString(), DslMethods.boxed(innerType), held, value.getChain(), genCtx);
            return;
        }
        if (value.getChain().isEmpty()) {
            sb.append(inner);
            return;
        }
        if (innerType == null && value.getParenInner().isLiteral("null")) {
            throw new IllegalArgumentException("A method is called on the literal null: " + value.toPathString());
        }
        // The chain is walked by reflection, each segment on what the one before returns, so the result is typed; a
        // group whose type is known only when the rule runs resolves on Object, checked against the list. A primitive
        // read is boxed, `("svc".length()).longValue()`, and never null, so a `?.` after it would only evaluate it
        // twice; one read behind a guard can be null.
        final String raw = innerType != null && innerType.isPrimitive() ? rawPrimitive(genCtx) : null;
        final String receiver = raw != null ? TALCodegenHelper.boxTypeName(innerType) + ".valueOf(" + raw + ")"
            : inner.toString();
        TALDefCodegen.generateDefVarChain(sb, new TALDefCodegen.ChainReceiver("(" + receiver + ")",
            innerType == null ? Object.class : DslMethods.boxed(innerType), null, held, raw == null),
            value.getChain(), genCtx);
    }

    // ==================== Binary expression codegen ====================

    private static boolean isWhole(final ExprType type) {
        return type == ExprType.INT || type == ExprType.LONG;
    }

    /**
     * Returns the JLS-style binary numeric promotion result for the two operand
     * types. Both must be numeric; otherwise this is a programmer bug (the
     * caller is responsible for the non-numeric error path).
     */
    private static ExprType promote(final ExprType a, final ExprType b) {
        if (a == ExprType.DOUBLE || b == ExprType.DOUBLE || a == ExprType.FLOAT || b == ExprType.FLOAT) {
            // A float computes in double, even with another float, as Groovy and LAL compute it.
            return ExprType.DOUBLE;
        }
        if (a == ExprType.LONG || b == ExprType.LONG) {
            return ExprType.LONG;
        }
        return ExprType.INT;
    }

    /**
     * Compile-time inference of an operand's Java type. Operates on the same
     * AST that codegen will subsequently emit, so the result is consistent.
     */
    private static ExprType inferType(final TALValueAccess part,
                                       final TALGenCtx genCtx) {
        if (part == null) {
            return ExprType.UNKNOWN;
        }
        if (part.isStringLiteral()) {
            return ExprType.STRING;
        }
        if (part.isNumberLiteral()) {
            return TALLiterals.NumericLiteral.parse(part.getSegments().get(0)).type;
        }
        // (expr as Cast) primary — only trustable when no further chain is applied.
        if (part.getParenInner() != null && part.getChain().isEmpty()) {
            if (part.getParenCast() != null) {
                final ExprType cast = ExprType.ofCast(part.getParenCast());
                // A class name, `(x as java.lang.Long)`, is the type its Java cast gives.
                return cast != ExprType.UNKNOWN ? cast : generatedType(part, genCtx);
            }
            // Pure grouping (no cast): the parens just preserve precedence.
            return inferType(part.getParenInner(), genCtx);
        }
        // `(span.name).length()`: the type the chain's own code resolves to.
        if (part.getParenInner() != null) {
            return generatedType(part, genCtx);
        }
        // Nested binary expression: recompute via its parts/ops.
        if (!part.getConcatParts().isEmpty()) {
            final ExprType inferred = inferBinaryType(part.getConcatParts(), part.getConcatOps(), genCtx);
            // A quotient beside a double is a double, which only the generated code knows.
            return inferred == ExprType.OBJECT || inferred == ExprType.UNKNOWN ? generatedType(part, genCtx) : inferred;
        }
        // def variable with a known resolved type.
        if (!part.getSegments().isEmpty()) {
            final TALLocalVar lv =
                genCtx.localVars.get(part.getSegments().get(0));
            if (lv != null && part.getChain().isEmpty()) {
                return exprTypeFromClass(lv.resolvedType);
            }
            if (lv != null) {
                // `pricing.inputEstimatedCostPerM`: the type the chain's own code resolves to.
                return generatedType(part, genCtx);
            }
        }
        // A built-in's return type is the DSL's to declare; without it the expression would fall back to string
        // concatenation and a numeric built-in could not take part in arithmetic.
        if (part.getFunctionCallName() != null) {
            final ExprType declared = genCtx.resolver.functionReturnType(part, genCtx);
            final Class<?> returnClass = declared == ExprType.OBJECT
                ? genCtx.resolver.functionReturnClass(part, genCtx) : null;
            if (part.getChain().isEmpty()) {
                // An object result that is a boxed number takes part in arithmetic as that number.
                if (returnClass != null) {
                    return exprTypeFromClass(returnClass);
                }
                if (declared != null) {
                    return declared;
                }
            } else if (returnClass != null || declared == ExprType.BOOLEAN || declared == ExprType.STRING
                || declared != null && declared.primitiveClass != null) {
                // `round(x).toString()`, `attr("x").length()`: the type the chain's own code resolves to.
                return generatedType(part, genCtx);
            }
        }
        // meta.*: the type the chain's own code resolves to, so a numeric field such as meta.latency is a number and
        // `meta.latency + 1` adds, a String field is a String, and `meta.latency.longValue()` is typed by its method.
        if (part.isMetaRef()) {
            return generatedType(part, genCtx);
        }
        // `span.name.length()`: a method after the fields resolves only in the chain's own code.
        if (part.isInputRef() && genCtx.inputType != null
                && part.getChain().stream().anyMatch(seg -> !(seg instanceof TALFieldSegment))) {
            return generatedType(part, genCtx);
        }
        if (part.isInputRef()
                && genCtx.resolver.isTypedInputActive()
                && genCtx.inputType != null) {
            final ExprType leaf = generatedType(part, genCtx);
            // Numeric and boolean leaves, primitive or boxed, so `span.retries + 1` adds; any other falls
            // through to UNKNOWN, read by the chain's own code.
            if (leaf.isNumeric() || leaf == ExprType.BOOLEAN) {
                return leaf;
            }
        }
        return ExprType.UNKNOWN;
    }

    /**
     * Map a Java {@link Class} (primitive or wrapper) to the matching
     * {@link ExprType}. Used when type information arrives as a Class —
     * from {@code def} variable resolution or proto-chain reflection —
     * and replaces what was two parallel if/else ladders.
     */
    private static ExprType exprTypeFromClass(final Class<?> c) {
        if (c == int.class || c == Integer.class || c == short.class || c == Short.class || c == byte.class
            || c == Byte.class) {
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
        return ExprType.OBJECT;
    }

    /**
     * The type an operand's own code resolves to, so arithmetic infers the type of the very method the emitted code
     * calls, overloads and indexes included.
     */
    private static ExprType generatedType(final TALValueAccess part, final TALGenCtx genCtx) {
        final Class<?> type = resolvedType(part, genCtx);
        return type == null ? ExprType.UNKNOWN : exprTypeFromClass(type);
    }

    /**
     * @return the static type of the value's code, generated into a scratch buffer only to read it; null when it is
     *     known only when the rule runs
     */
    private static Class<?> resolvedType(final TALValueAccess value, final TALGenCtx genCtx) {
        generateValueAccess(new StringBuilder(), value, genCtx);
        return genCtx.lastResolvedType;
    }

    /**
     * Walk the operand list applying JLS promotion left-to-right, treating any
     * {@code +} that touches a String operand as string concat (which sticks).
     */
    private static ExprType inferBinaryType(
            final List<? extends TALValueAccess> parts,
            final List<BinaryOp> ops,
            final TALGenCtx genCtx) {
        ExprType acc = inferType(parts.get(0), genCtx);
        for (int i = 1; i < parts.size(); i++) {
            final ExprType rhs = inferType(parts.get(i), genCtx);
            final BinaryOp op = ops.get(i - 1);
            if (op == BinaryOp.PLUS
                    && (acc == ExprType.STRING || rhs == ExprType.STRING)) {
                acc = ExprType.STRING;
                continue;
            }
            if (op == BinaryOp.SLASH && isWhole(acc) && isWhole(rhs)) {
                acc = ExprType.OBJECT;
                continue;
            }
            if (acc.isNumeric() && rhs.isNumeric()) {
                acc = promote(acc, rhs);
                continue;
            }
            // Anything else for +: the runtime `plus`, a number or a string as the values decide.
            if (op == BinaryOp.PLUS) {
                acc = ExprType.UNKNOWN;
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
            final List<? extends TALValueAccess> parts,
            final List<BinaryOp> ops,
            final TALGenCtx genCtx) {
        // Render the first operand and start the accumulator with its type.
        ExprType accType = inferType(parts.get(0), genCtx);
        final StringBuilder accBuf = new StringBuilder();
        appendOperandRaw(accBuf, parts.get(0), accType, genCtx);
        String accExpr = accBuf.toString();
        String accJoined = joinForm(parts.get(0), accType, accExpr, genCtx);
        String accObject = objectForm(parts.get(0), genCtx);
        boolean accString = isStaticString(parts.get(0), accType, genCtx);
        // A decimal quotient, or another number Java has no arithmetic for, such as a BigDecimal: its class.
        Class<?> accDecimal = decimalClass(parts.get(0), accType, genCtx);

        for (int i = 1; i < parts.size(); i++) {
            final BinaryOp op = ops.get(i - 1);
            final ExprType rhsType = inferType(parts.get(i), genCtx);
            final StringBuilder rhsBuf = new StringBuilder();
            appendOperandRaw(rhsBuf, parts.get(i), rhsType, genCtx);
            final String rhsExpr = rhsBuf.toString();

            final Class<?> rhsDecimal = decimalClass(parts.get(i), rhsType, genCtx);
            final boolean accNumber = accType.isNumeric() || accDecimal != null;
            final boolean rhsNumber = rhsType.isNumeric() || rhsDecimal != null;
            if (accNumber && rhsNumber && (accDecimal != null || rhsDecimal != null
                || op == BinaryOp.SLASH && isWhole(accType) && isWhole(rhsType))) {
                if (isFloatingType(accType.primitiveClass) || isFloatingType(rhsType.primitiveClass)) {
                    // Beside a float or a double, in double, as Groovy computes it: a zero divisor is an infinity.
                    final String l = accDecimal != null ? TALRuntimeRoles.method("toDouble") + "(" + accObject + ")"
                        : widenPrimitiveExpr(accExpr, accType, ExprType.DOUBLE);
                    final String r = rhsDecimal != null
                        ? TALRuntimeRoles.method("toDouble") + "(" + objectForm(parts.get(i), genCtx) + ")"
                        : widenPrimitiveExpr(rhsExpr, rhsType, ExprType.DOUBLE);
                    accExpr = "(" + l + " " + op.symbol() + " " + r + ")";
                    accType = ExprType.DOUBLE;
                    accDecimal = null;
                    accJoined = accExpr;
                    accObject = "Double.valueOf(" + accExpr + ")";
                    continue;
                }
                // A quotient of whole numbers is the exact decimal, held until where it goes converts it, and so is
                // arithmetic beside it; a number of a class the rule does not know computes as Groovy's.
                final boolean exact = (accDecimal == null || accDecimal == BigDecimal.class)
                    && (rhsDecimal == null || rhsDecimal == BigDecimal.class);
                if (op == BinaryOp.SLASH && (isOpenNumber(accDecimal) || isOpenNumber(rhsDecimal))) {
                    // Whether it divides as a decimal or as a double depends on a class known only when the rule runs.
                    throw new IllegalArgumentException(
                        "'/' of a number whose class is known only when the rule runs, such as a java.lang.Number, "
                            + "divides as a decimal or as a double by that class; convert it with as Long or as "
                            + "Double first");
                }
                final String helper = op == BinaryOp.SLASH ? "quotient"
                    : op == BinaryOp.PLUS ? exact ? "exactSum" : "sum"
                    : op == BinaryOp.MINUS ? exact ? "exactDifference" : "minus"
                    : exact ? "exactProduct" : "times";
                accExpr = TALRuntimeRoles.method(helper) + "(" + accObject + ", " + objectForm(parts.get(i), genCtx)
                    + ")";
                // Groovy keeps whole numbers beside a BigInteger whole.
                final boolean integral = (accDecimal == BigInteger.class || accDecimal == null && isWhole(accType))
                    && (rhsDecimal == BigInteger.class || rhsDecimal == null && isWhole(rhsType));
                accType = ExprType.OBJECT;
                accDecimal = op == BinaryOp.SLASH || exact ? BigDecimal.class : integral ? BigInteger.class
                    : Number.class;
                accJoined = accExpr;
                accObject = accExpr;
                continue;
            }
            if (accType.isNumeric() && rhsType.isNumeric()) {
                final ExprType promoted = promote(accType, rhsType);
                final String widenedAcc = widenPrimitiveExpr(accExpr, accType, promoted);
                final String widenedRhs = widenPrimitiveExpr(rhsExpr, rhsType, promoted);
                accExpr = "(" + widenedAcc + " " + op.symbol() + " " + widenedRhs + ")";
                accType = promoted;
                accJoined = accExpr;
                accObject = promoted.wrapperName + ".valueOf(" + accExpr + ")";
                continue;
            }
            if (op != BinaryOp.PLUS) {
                final boolean left = !accType.isNumeric();
                final String operand = left && i > 1 ? "the value before it"
                    : "`" + operandText(parts.get(left ? 0 : i)) + "`";
                final String kind = left && i > 1 ? (accString ? "a String" : "a value whose type is known only when "
                    + "the rule runs") : describeKind(parts.get(left ? 0 : i), left ? accType : rhsType, genCtx);
                throw new IllegalArgumentException(
                    "'" + op.symbol() + "' takes numbers only, and " + operand + " is " + kind
                        + "; convert it with as Integer, Long, Float or Double");
            }
            final boolean rhsString = isStaticString(parts.get(i), rhsType, genCtx);
            if (i == 1 && isStaticBoolean(parts.get(0), accType, genCtx)
                || isStaticBoolean(parts.get(i), rhsType, genCtx) && !accString && accNumber) {
                // Groovy has no plus on a boolean, nor a number's plus taking one; only a string joins one
                throw new IllegalArgumentException("A boolean takes no part in '+': only a string joins one "
                    + "(\"x\" + true)");
            }
            if (i == 1 && rhsString) {
                // a text joins only onto a text, a number, a character or null; a value of another known type is
                // refused now, one typed only when the rule runs by plus then
                final Class<?> leftType = staticClass(parts.get(0), accType, genCtx);
                if (!DslNumbers.mayJoinText(leftType)) {
                    throw DslNumbers.cannotJoinText(leftType);
                }
            }
            if (rhsString && !accString && !accNumber) {
                // a value typed only when the rule runs beside a string: plus refuses a boolean there, else joins
                accExpr = "((String) " + TALRuntimeRoles.method("plus") + "(" + accObject + ", "
                    + objectForm(parts.get(i), genCtx) + "))";
                accType = ExprType.STRING;
                accString = true;
            } else if (accString || rhsString) {
                // Java's concatenation, a boxed number as its box, so a missing one joins as "null"; an array, a
                // collection or a map is no text, refused now where its type is known, else when the rule runs.
                final Class<?> rhsStatic = staticClass(parts.get(i), rhsType, genCtx);
                if (DslNumbers.isItems(rhsStatic)) {
                    throw DslNumbers.cannotJoinText(rhsStatic);
                }
                final String rhsForm = joinForm(parts.get(i), rhsType, rhsExpr, genCtx);
                final String rhsJoined = rhsType == ExprType.OBJECT || rhsType == ExprType.UNKNOWN
                    ? TALRuntimeRoles.method("joined") + "(" + rhsForm + ")" : rhsForm;
                // A grouped join, `n + (m + s)`, joins as one operand: Java's `n + m + s` would add the numbers first.
                accExpr = accJoined + " + " + (rhsType == ExprType.STRING && isGroupedJoin(parts.get(i))
                    ? "(" + rhsJoined + ")" : rhsJoined);
                accType = ExprType.STRING;
                accString = true;
            } else {
                // Neither side is a string when the rule compiles: numbers add and other values join, as in LAL.
                accExpr = TALRuntimeRoles.method("plus") + "(" + accObject + ", " + objectForm(parts.get(i), genCtx)
                    + ")";
                accType = ExprType.UNKNOWN;
            }
            accDecimal = null;
            accJoined = accExpr;
            accObject = accExpr;
        }

        if (accType.isNumeric()) {
            // Expose the primitive form so the caller (arithmetic, a primitive argument) can avoid a conversion; emit a
            // boxed form into sb for Object contexts (tag assignments, def initialisers, ...).
            genCtx.lastResolvedType = accType.primitiveClass;
            genCtx.lastRawChain = accExpr;
            genCtx.lastNullChecks = false;
            sb.append(accType.wrapperName).append(".valueOf(").append(accExpr).append(")");
        } else {
            // Non-numeric end state: a String concatenation, or the runtime `plus`. Update the genCtx metadata
            // explicitly — otherwise a stale numeric resolved type from an inner operand would leak out and mis-type
            // things like `def msg = "count=" + (attr("n") as Integer)`, which the def codegen reads from
            // lastResolvedType to declare the local variable.
            genCtx.lastResolvedType = accString ? String.class : accDecimal != null ? accDecimal : Object.class;
            genCtx.lastRawChain = null;
            genCtx.lastNullChecks = false;
            sb.append(accExpr);
        }
    }

    private static boolean isGroupedJoin(final TALValueAccess part) {
        if (!part.getChain().isEmpty() || part.getParenCast() != null) {
            return false;
        }
        return !part.getConcatParts().isEmpty() || part.getParenInner() != null && isGroupedJoin(part.getParenInner());
    }

    private static boolean isOpenNumber(final Class<?> decimal) {
        return decimal != null && decimal != BigDecimal.class && decimal != BigInteger.class;
    }

    /**
     * @return the class of an operand that is a number Java has no arithmetic for, a decimal quotient or a
     * {@code BigDecimal}; null for any other operand
     */
    private static Class<?> decimalClass(final TALValueAccess part, final ExprType type, final TALGenCtx genCtx) {
        final Class<?> resolved = staticClass(part, type, genCtx);
        return resolved != null && Number.class.isAssignableFrom(resolved)
            && exprTypeFromClass(resolved) == ExprType.OBJECT ? resolved : null;
    }

    /**
     * @return the class an operand's code resolves to when its inferred type says no more than an object, else null
     */
    private static Class<?> staticClass(final TALValueAccess part, final ExprType type, final TALGenCtx genCtx) {
        return type == ExprType.OBJECT || type == ExprType.UNKNOWN ? resolvedType(part, genCtx) : null;
    }

    private static boolean isSafeNavigation(final TALValueAccessSegment segment) {
        return segment instanceof TALMethodSegment && ((TALMethodSegment) segment).isSafeNav()
            || segment instanceof TALFieldSegment && ((TALFieldSegment) segment).isSafeNav();
    }

    /**
     * {@code (value)?.chain} on a value that can be null: the value is held once, where the expression runs, so the
     * null check of {@code ?.} does not evaluate it, and whatever it changes, a second time.
     */
    private static void heldChain(final StringBuilder sb, final String value, final Class<?> type,
                                  final TALValueAccess heldValue,
                                  final List<? extends TALValueAccessSegment> chain, final TALGenCtx genCtx) {
        // One local per held expression: the code is rendered again to type it, and each rendering would declare one.
        String held = genCtx.heldLocals.get(value);
        if (held == null) {
            held = genCtx.nextLocalName("_held");
            genCtx.localVarDecls.append("  java.lang.Object ").append(held).append(" = null;\n");
            genCtx.localVarLvtVars.add(new String[] {held, "Ljava/lang/Object;"});
            genCtx.heldLocals.put(value, held);
        }
        final StringBuilder rest = new StringBuilder();
        TALDefCodegen.generateDefVarChain(rest, new TALDefCodegen.ChainReceiver("((" + TALCodegenHelper.javaTypeName(type)
            + ") " + held + ")", type, null, heldValue, true), chain, genCtx);
        Class<?> result = genCtx.lastResolvedType;
        final Class<?> elementType = genCtx.lastResolvedElementType;
        String restExpr = rest.toString();
        if (result != null && result.isPrimitive()) {
            // A primitive behind the guard is boxed already; one read without it is boxed here.
            final String raw = rawPrimitive(genCtx);
            if (raw != null) {
                restExpr = TALCodegenHelper.boxTypeName(result) + ".valueOf(" + raw + ")";
            }
            result = DslMethods.boxed(result);
        }
        genCtx.clearExtraLogResult();
        final Class<?> resultType = result == null ? Object.class : result;
        sb.append("((").append(TALCodegenHelper.javaTypeName(resultType)).append(") ")
          .append(TALRuntimeRoles.method("first")).append("(").append(held).append(" = ").append(value).append(", ")
          .append(restExpr).append("))");
        genCtx.lastResolvedType = resultType;
        genCtx.lastResolvedElementType = elementType;
    }

    /**
     * @return whether the value's code reads a primitive, which can never be null
     */
    static boolean readsPrimitive(final TALValueAccess value, final TALGenCtx genCtx) {
        resolvedType(value, genCtx);
        final boolean primitive = rawPrimitive(genCtx) != null;
        genCtx.clearExtraLogResult();
        return primitive;
    }

    /**
     * Refuses a character cast as a number, which Groovy read as its code; a {@code Boolean} cast reads its truth.
     */
    private static void requireNoCharacter(final Class<?> type, final TALValueAccess value, final String castType) {
        if ((type == char.class || type == Character.class) && !"Boolean".equals(castType)) {
            throw DslNumbers.characterAsNumber(operandText(value), castType);
        }
    }

    private static boolean isJson(final Class<?> type) {
        return type != null && JsonElement.class.isAssignableFrom(type);
    }

    private static boolean holdsItems(final Class<?> type) {
        return type != null && DslValues.comparesByContent(type);
    }

    private static boolean isStaticBoolean(final TALValueAccess part, final ExprType type, final TALGenCtx genCtx) {
        final Class<?> known = type == ExprType.BOOLEAN ? boolean.class : staticClass(part, type, genCtx);
        return known == boolean.class || known == Boolean.class;
    }

    /**
     * @return whether an operand is a String when the rule compiles
     */
    private static boolean isStaticString(final TALValueAccess part, final ExprType type, final TALGenCtx genCtx) {
        return type == ExprType.STRING || staticClass(part, type, genCtx) == String.class;
    }

    /**
     * @return the operand as Java's concatenation takes it: a boxed number as the box, which joins a missing value
     * as "null", any other operand as its raw form
     */
    private static String joinForm(final TALValueAccess part, final ExprType type, final String raw,
                                   final TALGenCtx genCtx) {
        if (!type.isNumeric()) {
            return raw;
        }
        final StringBuilder value = new StringBuilder();
        generateValueAccess(value, part, genCtx);
        final Class<?> resolved = genCtx.lastResolvedType;
        // A primitive read behind a `?.` guard is already boxed, and can be null.
        final boolean guarded = resolved != null && resolved.isPrimitive() && rawPrimitive(genCtx) == null;
        genCtx.clearExtraLogResult();
        return resolved != null && (!resolved.isPrimitive() || guarded) && part.getConcatParts().isEmpty()
            ? value.toString() : raw;
    }

    /**
     * @return the operand as an object, a primitive boxed, for the runtime {@code plus}
     */
    private static String objectForm(final TALValueAccess part, final TALGenCtx genCtx) {
        final StringBuilder boxed = new StringBuilder();
        generateBoxedValueAccess(boxed, part, null, genCtx);
        genCtx.clearExtraLogResult();
        return boxed.toString();
    }

    private static String operandText(final TALValueAccess part) {
        if (part.isStringLiteral()) {
            return "\"" + part.getSegments().get(0) + "\"";
        }
        return part.getParenInner() == null ? part.toPathString()
            : "(" + part.getParenInner().toPathString()
                + (part.getParenCast() == null ? "" : " as " + part.getParenCast()) + ")";
    }

    /**
     * @return what a non-numeric operand of arithmetic is, for the message
     */
    private static String describeKind(final TALValueAccess part, final ExprType type, final TALGenCtx genCtx) {
        if (type == ExprType.STRING) {
            return "a String";
        }
        if (type == ExprType.BOOLEAN) {
            return "a boolean";
        }
        final Class<?> resolved = staticClass(part, type, genCtx);
        return resolved == null || resolved == Object.class ? "a value whose type is known only when the rule runs"
            : "a " + resolved.getName();
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
                                          final TALValueAccess part,
                                          final ExprType partType,
                                          final TALGenCtx genCtx) {
        if (part.isNumberLiteral()) {
            sb.append(TALLiterals.NumericLiteral.parse(part.getSegments().get(0)).javaText);
            return;
        }
        if (part.getParenInner() != null && ExprType.ofCast(part.getParenCast()).isNumeric()
                && part.getChain().isEmpty() && partType.isNumeric()) {
            generateCastedValueAccess(sb, part.getParenInner(),
                partType.wrapperName, genCtx);
            return;
        }
        if (part.getParenInner() != null && part.getParenCast() == null
                && part.getChain().isEmpty()) {
            final StringBuilder inner = new StringBuilder();
            generateValueAccessObj(inner, part.getParenInner(), null, genCtx);
            final String raw = rawPrimitive(genCtx);
            if (partType.isNumeric() && raw != null) {
                sb.append(raw);
            } else if (partType.isNumeric()) {
                // A grouped boxed number, such as a def variable's: unboxed for the arithmetic.
                sb.append(wrapAsPrimitive(inner.toString(), partType));
            } else {
                sb.append(inner);
            }
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
            // A primitive read takes part raw, widened to the arithmetic's type; any other operand, a boxed number or a
            // primitive read behind a `?.` guard, converts through the type's conversion, which fails on a null.
            final StringBuilder buf = new StringBuilder();
            generateValueAccess(buf, part, genCtx);
            final String raw = rawPrimitive(genCtx);
            if (raw != null && isNumericPrimitiveClass(genCtx.lastResolvedType)) {
                sb.append(widenPrimitiveExpr(raw, exprTypeFromClass(genCtx.lastResolvedType), partType));
            } else {
                sb.append(wrapAsPrimitive(buf.toString(), partType));
            }
        } else {
            generateValueAccess(sb, part, genCtx);
        }
    }
}
