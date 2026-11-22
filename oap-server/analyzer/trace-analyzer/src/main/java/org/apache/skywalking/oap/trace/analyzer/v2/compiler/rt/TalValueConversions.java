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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;
import org.apache.skywalking.oap.server.core.dsl.DslValues;

/**
 * How generated code converts and compares values: the {@code as} conversions, Javassist's missing autoboxing,
 * conditions over a bare expression, typed {@code Layer} and enum values, and the list a {@code foreach} walks. Not a
 * function a rule calls.
 *
 * <p>The conversions behave as {@code LalRuntimeHelper}'s in {@code log-analyzer} do.
 */
public final class TalValueConversions {
    private TalValueConversions() {
    }

    // ==================== Comparisons ====================

    /**
     * {@code value as Integer} where a null may stay null, as LAL casts: a comparison operand, a def. A blank text
     * is a missing value too, as {@code attr()} reads an absent attribute. A value that cannot be converted fails
     * the span, as {@link #toInt(Object)} does.
     *
     * @return the Integer, or null for a missing value
     */
    public static Integer asInteger(final Object value) {
        return DslNumbers.isBlank(value) ? null : Integer.valueOf(toInt(value));
    }

    public static Long asLong(final Object value) {
        return DslNumbers.isBlank(value) ? null : Long.valueOf(toLong(value));
    }

    public static Double asDouble(final Object value) {
        return DslNumbers.isBlank(value) ? null : Double.valueOf(toDouble(value));
    }

    public static Float asFloat(final Object value) {
        return DslNumbers.isBlank(value) ? null : Float.valueOf(toFloat(value));
    }

    /**
     * {@code value as Boolean} where a null may stay null.
     *
     * @return the value as a boolean, as LAL converts it ({@link DslNumbers#toBoolean}); null for null or a blank text
     */
    public static Boolean asBoolean(final Object value) {
        return DslNumbers.isBlank(value) ? null : Boolean.valueOf(DslNumbers.toBoolean(value));
    }

    /**
     * A {@code log} argument after the message's placeholders, whose type is known only when the rule runs: SLF4J
     * logs a Throwable there as the exception and drops anything else, so anything else fails the rule instead.
     *
     * @param value the argument
     * @return the argument, a Throwable or null
     */
    public static Object loggedException(final Object value) {
        if (value == null || value instanceof Throwable) {
            return value;
        }
        throw new IllegalArgumentException(
            "a log argument after the message's placeholders is logged as the exception, and a "
                + value.getClass().getName() + " is none; add a {} for it");
    }

    /**
     * Every comparison but a null check and a Layer or an enum against a literal: {@code ==} and {@code !=} as every
     * DSL compares values ({@link DslValues#equal}); an ordering as every DSL orders values ({@link DslValues#order}),
     * two booleans as Java orders them, and a null below every value.
     *
     * @param op the operator, the ordinal of {@code CompareOp}: ==, !=, &gt;, &lt;, &gt;=, &lt;=
     * @return the comparison
     */
    public static boolean compareOrdered(final Object left, final Object right, final int op) {
        if (op == 0 || op == 1) {
            return equal(left, right) == (op == 0);
        }
        if (left == null || right == null) {
            return holds(left == right ? 0 : left == null ? -1 : 1, op);
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return holds(Boolean.compare((Boolean) left, (Boolean) right), op);
        }
        if (!DslValues.ordersByValue(left, right)) {
            throw new IllegalArgumentException(
                "cannot order " + describe(left) + " against " + describe(right) + "; convert it explicitly");
        }
        final Integer order = DslValues.order(left, right);
        // a NaN has no order, so every ordering of it is false
        return order != null && holds(order, op);
    }

    /**
     * @return the value as a message names it, a character or any other text as the String it is
     */
    private static String describe(final Object value) {
        final Object shown = value instanceof Character || value instanceof CharSequence ? value.toString() : value;
        return shown.getClass().getName() + " '" + shown + "'";
    }

    private static boolean holds(final int sign, final int op) {
        switch (op) {
            case 0:
                return sign == 0;
            case 1:
                return sign != 0;
            case 2:
                return sign > 0;
            case 3:
                return sign < 0;
            case 4:
                return sign >= 0;
            case 5:
            default:
                return sign <= 0;
        }
    }

    // ==================== Conversions ====================

    /**
     * Javassist does not autobox, so the primitive results of a chain or a function reach the conversions as
     * primitives and need their own overloads.
     */
    public static int toInt(final int value) {
        return value;
    }

    public static int toInt(final long value) {
        return (int) value;
    }

    public static long toLong(final long value) {
        return value;
    }

    public static long toLong(final int value) {
        return value;
    }

    public static double toDouble(final double value) {
        return value;
    }

    public static float toFloat(final float value) {
        return value;
    }

    public static boolean toBool(final boolean value) {
        return value;
    }

    public static String toStr(final int value) {
        return String.valueOf(value);
    }

    public static String toStr(final long value) {
        return String.valueOf(value);
    }

    /**
     * A character is written as the one-character string it is, not as its code.
     */
    public static String toStr(final char value) {
        return String.valueOf(value);
    }

    /**
     * {@code +} where neither side is a string nor both numbers when the rule compiles, as LAL's: two numbers add in
     * Groovy's number type ({@code Integer + Long} is a {@code Long}, a float or a double makes a {@code Double}), and
     * any other values, a null or a character included, concatenate.
     */
    public static Object plus(final Object left, final Object right) {
        return DslNumbers.plus(left, right);
    }

    public static String joined(final Object value) {
        return DslNumbers.joined(value);
    }

    public static String toStr(final boolean value) {
        return String.valueOf(value);
    }

    /**
     * {@code as String} and a value written as a string, as LAL converts: an array by its elements; null stays null.
     */
    public static String toStr(final Object obj) {
        return obj == null ? null : DslStrings.text(obj);
    }

    /**
     * {@code as Long} and a value passed to a {@code long} parameter, as LAL converts: a number, or a string trimmed
     * and parsed; anything else, a null included, fails the span.
     */
    public static long toLong(final Object obj) {
        return DslNumbers.toLong(obj);
    }

    public static int toInt(final Object obj) {
        return DslNumbers.toInt(obj);
    }

    public static double toDouble(final Object obj) {
        return DslNumbers.toDouble(obj);
    }

    public static float toFloat(final Object obj) {
        return DslNumbers.toFloat(obj);
    }

    /**
     * {@code /} on two whole numbers, or on a decimal quotient: the exact decimal, as Groovy divides (7 / 2 is 3.5),
     * and ten decimals rounded half up where it does not terminate; a zero divisor fails the span. The value stays that
     * decimal until where it goes converts it: an int or long truncates it, a double reads its double value, text its
     * digits, and a comparison its exact value.
     */
    public static BigDecimal quotient(final Object dividend, final Object divisor) {
        if (DslNumbers.isWholeBox(dividend) && DslNumbers.isWholeBox(divisor)) {
            final long l = ((Number) dividend).longValue();
            final long r = ((Number) divisor).longValue();
            // A whole quotient, such as nanoseconds to milliseconds, skips the decimal division.
            if (r != 0 && l % r == 0 && !(l == Long.MIN_VALUE && r == -1)) {
                return BigDecimal.valueOf(l / r);
            }
        }
        return DslNumbers.quotient(DslNumbers.exact((Number) dividend), DslNumbers.exact((Number) divisor));
    }

    /**
     * {@code +}, {@code -} or {@code *} of a decimal quotient and a whole number or another decimal: exact.
     */
    public static BigDecimal exactSum(final Object left, final Object right) {
        return DslNumbers.exact((Number) left).add(DslNumbers.exact((Number) right));
    }

    public static BigDecimal exactDifference(final Object left, final Object right) {
        return DslNumbers.exact((Number) left).subtract(DslNumbers.exact((Number) right));
    }

    public static BigDecimal exactProduct(final Object left, final Object right) {
        return DslNumbers.exact((Number) left).multiply(DslNumbers.exact((Number) right));
    }

    public static Number sum(final Object left, final Object right) {
        return DslNumbers.arithmetic((Number) left, (Number) right, '+');
    }

    /**
     * {@code +}, {@code -} or {@code *} with a number of a class the rule does not know when it compiles, such as a
     * JSON number, as Groovy computes it: in double beside a float or a double, else exactly.
     */
    public static Number minus(final Object left, final Object right) {
        return DslNumbers.arithmetic((Number) left, (Number) right, '-');
    }

    public static Number times(final Object left, final Object right) {
        return DslNumbers.arithmetic((Number) left, (Number) right, '*');
    }

    /**
     * Evaluates its arguments in order and answers the second: a value a guarded chain reads is held once, so its
     * null check does not run it a second time.
     */
    public static Object first(final Object held, final Object value) {
        return value;
    }

    /**
     * {@code as Boolean} to a primitive, as LAL converts ({@link DslNumbers#toBoolean}); a null or a blank text, a
     * missing value, fails the span.
     */
    public static boolean toBool(final Object obj) {
        if (DslNumbers.isBlank(obj)) {
            throw DslNumbers.cannotConvert(obj, "boolean");
        }
        return DslNumbers.toBoolean(obj);
    }

    /**
     * A value passed to a boxed scalar parameter, such as an {@code Integer} setter: converted as its primitive
     * converts, a null kept rather than turned into zero.
     *
     * @param type  the wrapper the parameter takes
     * @param value the value
     * @return the converted value, or null
     */
    public static Object toBoxed(final Class type, final Object value) {
        if (value == null) {
            return null;
        }
        if (type == Integer.class) {
            return Integer.valueOf(toInt(value));
        }
        if (type == Long.class) {
            return Long.valueOf(toLong(value));
        }
        if (type == Double.class) {
            return Double.valueOf(toDouble(value));
        }
        if (type == Float.class) {
            return Float.valueOf(toFloat(value));
        }
        if (type == Short.class) {
            return Short.valueOf((short) toInt(value));
        }
        if (type == Byte.class) {
            return Byte.valueOf((byte) toInt(value));
        }
        return Boolean.valueOf(toBool(value));
    }

    /**
     * A bare {@code as Boolean} condition: the value as the cast converts it ({@link #asBoolean(Object)}), a missing
     * value, null or a blank text, false.
     *
     * @param obj the value the condition read
     * @return whether the condition holds
     */
    public static boolean isTrue(final Object obj) {
        return !DslNumbers.isBlank(obj) && DslNumbers.toBoolean(obj);
    }

    /**
     * The truth of an {@code if} over a bare expression, as LAL's: see {@link DslValues#isNotEmpty}.
     */
    public static boolean isNotEmpty(final Object obj) {
        return DslValues.isNotEmpty(obj);
    }

    /**
     * {@code ==} by content, as every DSL compares values: see {@link DslValues#equal}.
     */
    public static boolean equal(final Object left, final Object right) {
        return DslValues.equal(left, right);
    }

    /**
     * {@code value?.toString()}: null for a null, else as {@link #text}.
     */
    public static String toString(final Object obj) {
        return obj == null ? null : DslStrings.text(obj);
    }

    /**
     * {@code value.toString()} on an array, a collection, a map or a value of unknown type: by content, see
     * {@link DslStrings#text}; a null fails.
     */
    public static String text(final Object obj) {
        return DslStrings.text(obj);
    }

    // ==================== Typed values and loops ====================

    /**
     * {@code as Layer}: a {@link Layer} as it is, a name through {@link Layer#valueOf(String)}, which fails on a
     * name no layer registered; {@code null} stays {@code null}.
     *
     * @param value the value to convert
     * @return the layer, or null
     */
    public static Layer toLayer(final Object value) {
        if (value == null || value instanceof Layer) {
            return (Layer) value;
        }
        return Layer.valueOf(String.valueOf(value));
    }

    /**
     * {@code as <Enum>}: a constant of the enum as it is, a name through {@link Enum#valueOf}, which fails on a
     * name that is none of its constants; {@code null} stays {@code null}.
     *
     * @param type  the enum class
     * @param value the value to convert
     * @return the constant, or null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object toEnum(final Class type, final Object value) {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        return Enum.valueOf(type, String.valueOf(value));
    }

    /**
     * A {@code Layer} or an enum value compared with a string literal, which compares by name.
     *
     * @param value the typed value, possibly null
     * @param name  the literal, already checked when the rule compiled
     * @param equal true for {@code ==}, false for {@code !=}
     * @return whether the comparison holds
     */
    public static boolean sameName(final Object value, final String name, final boolean equal) {
        final String actual = value instanceof Layer ? ((Layer) value).name()
            : value instanceof Enum ? ((Enum<?>) value).name() : null;
        return name.equals(actual) == equal;
    }

    /**
     * The iterator a {@code foreach} walks; a null list has no element.
     *
     * @param list the list the rule names
     * @return its iterator
     */
    public static Iterator<?> iterate(final Object list) {
        return list == null ? Collections.emptyIterator() : ((List<?>) list).iterator();
    }
}
