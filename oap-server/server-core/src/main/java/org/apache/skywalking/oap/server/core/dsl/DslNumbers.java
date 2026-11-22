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
 *
 */

package org.apache.skywalking.oap.server.core.dsl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Numbers in the DSL engines: reading a number from text and from a value typed only at run time (a log's parsed JSON,
 * a span attribute), and comparing and computing numbers of any {@link Number} type.
 */
public final class DslNumbers {

    private DslNumbers() {
    }

    /**
     * @return whether a numeric cast reads the value as missing: a null, or a text that is blank once trimmed
     */
    public static boolean isBlank(final Object value) {
        return value == null || value instanceof CharSequence && value.toString().trim().isEmpty();
    }

    /**
     * @return the number a text holds, trimmed as Groovy's conversion trims it; null when it holds none
     */
    public static BigDecimal numberIn(final CharSequence text) {
        try {
            return new BigDecimal(text.toString().trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@code as Long}: a number, or a text trimmed and parsed; a character and anything else fail.
     */
    public static long toLong(final Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(text(value, "long"));
    }

    public static int toInt(final Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(text(value, "int"));
    }

    public static double toDouble(final Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(text(value, "double"));
    }

    public static float toFloat(final Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(text(value, "float"));
    }

    /**
     * {@code as Boolean} in LAL and TAL: a boolean as itself, the text {@code true} or {@code false} (any case), a
     * number by zero (0 is false, any other number true) and a text holding a number as that number (trimmed); any
     * other value has no boolean meaning ({@code "yes"}, NaN, a list) and fails. Each engine decides a null or a blank
     * text before.
     */
    public static boolean toBoolean(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            final String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return "true".equalsIgnoreCase(text);
            }
            final BigDecimal number = numberIn(text);
            if (number != null) {
                return number.signum() != 0;
            }
        } else if (value instanceof BigDecimal || value instanceof BigInteger) {
            return value instanceof BigDecimal ? ((BigDecimal) value).signum() != 0 : ((BigInteger) value).signum() != 0;
        } else if (value instanceof Number && !Double.isNaN(((Number) value).doubleValue())) {
            return ((Number) value).doubleValue() != 0;
        }
        throw new IllegalArgumentException(describe(value) + " cannot be converted to boolean: only a boolean, the "
            + "text true or false, and a number or a text holding one (0 is false, any other number true) are");
    }

    /**
     * @return the trimmed text a numeric conversion parses; a character or a value of another type fails
     */
    private static String text(final Object value, final String type) {
        if (value instanceof Character) {
            throw characterAsNumber("'" + value + "'", type);
        }
        if (value instanceof CharSequence) {
            return value.toString().trim();
        }
        throw cannotConvert(value, type);
    }

    /**
     * A character cast as a number, which LAL and TAL refuse: Groovy read its code ({@code '5' as Integer} is 53), its
     * string names the number it is.
     *
     * @param described the value, for the message
     * @param type      the number type cast to
     */
    public static IllegalArgumentException characterAsNumber(final String described, final String type) {
        return new IllegalArgumentException("A character cannot be cast as a number: " + described + " as " + type
            + "; Groovy read its code ('5' as Integer is 53). Cast its string, c.toString() as " + type
            + ", for the number it is");
    }

    public static boolean isFloating(final Object value) {
        return value instanceof Double || value instanceof Float;
    }

    /**
     * @return whether the two numbers have no order, a NaN on either side, which Java compares false but in
     *     {@code !=}; {@link #compare} must not be asked then, {@link #order} answers null
     */
    public static boolean isUnordered(final Number left, final Number right) {
        return Double.isNaN(left.doubleValue()) || Double.isNaN(right.doubleValue());
    }

    /**
     * @return the order of two numbers as {@link #compare} gives it, null when a NaN stands on either side, which has
     *     none
     */
    public static Integer order(final Number left, final Number right) {
        return isUnordered(left, right) ? null : compare(left, right);
    }

    /**
     * The order of two numbers that are not {@link #isUnordered}: as longs for two whole boxes; in double, as Java
     * compares doubles (-0.0 equals 0.0), when either is a float or a double; else by the exact decimal values (a
     * {@code BigDecimal}, a {@code BigInteger}, a text read as a number, a JSON number Gson keeps lazily, any other
     * {@link Number}).
     */
    public static int compare(final Number left, final Number right) {
        if (isWholeBox(left) && isWholeBox(right)) {
            return Long.compare(left.longValue(), right.longValue());
        }
        final BigDecimal l = isFloating(left) || isFloating(right) ? null : exact(left);
        final BigDecimal r = l == null ? null : exact(right);
        if (l == null || r == null) {
            final double a = left.doubleValue();
            final double b = right.doubleValue();
            return a < b ? -1 : a > b ? 1 : 0;
        }
        return l.compareTo(r);
    }

    public static Number add(final Number left, final Number right) {
        return arithmetic(left, right, '+');
    }

    /**
     * Groovy's number {@code +}, {@code -} and {@code *}: in double with a float or a double, or a number of another
     * type that is NaN or infinite; by the exact decimal value with a {@code BigDecimal} or a {@link Number} of
     * another type; a {@code BigInteger} with whole numbers; else a long when either is one, an int (wrapping)
     * otherwise.
     *
     * @param op {@code '+'}, {@code '-'} or {@code '*'}
     */
    public static Number arithmetic(final Number left, final Number right, final char op) {
        final boolean leftWhole = isWholeBox(left) || left instanceof BigInteger;
        final boolean rightWhole = isWholeBox(right) || right instanceof BigInteger;
        final BigDecimal l = isFloating(left) || isFloating(right) || leftWhole && rightWhole ? null : exact(left);
        final BigDecimal r = l == null ? null : exact(right);
        if (isFloating(left) || isFloating(right) || !(leftWhole && rightWhole) && (l == null || r == null)) {
            final double a = left.doubleValue();
            final double b = right.doubleValue();
            return op == '+' ? a + b : op == '-' ? a - b : a * b;
        }
        if (!leftWhole || !rightWhole) {
            return op == '+' ? l.add(r) : op == '-' ? l.subtract(r) : l.multiply(r);
        }
        if (left instanceof BigInteger || right instanceof BigInteger) {
            final BigDecimal a = exact(left);
            final BigDecimal b = exact(right);
            return (op == '+' ? a.add(b) : op == '-' ? a.subtract(b) : a.multiply(b)).toBigInteger();
        }
        if (left instanceof Long || right instanceof Long) {
            final long a = left.longValue();
            final long b = right.longValue();
            return op == '+' ? a + b : op == '-' ? a - b : a * b;
        }
        final int a = left.intValue();
        final int b = right.intValue();
        return op == '+' ? a + b : op == '-' ? a - b : a * b;
    }

    /**
     * {@code +} of two values LAL or TAL types only when the rule runs: two numbers add, as {@link #add}; a boolean on
     * the left, or on the right of anything but a text, fails, as Groovy has no such {@code plus}; otherwise the two
     * join as text when the left one {@link #joinsText}, and fail when it does not.
     */
    public static Object plus(final Object left, final Object right) {
        if (left instanceof Boolean || right instanceof Boolean && !(left instanceof CharSequence)) {
            throw new IllegalArgumentException("Cannot add " + describe(left) + " and " + describe(right)
                + ": a boolean has no +, only a text joins one (\"x\" + true)");
        }
        if (left instanceof Number && right instanceof Number) {
            return add((Number) left, (Number) right);
        }
        if (!joinsText(left)) {
            throw cannotJoinText(left.getClass());
        }
        return String.valueOf(left) + joined(right);
    }

    /**
     * @return a value joined after a text: its text; an array, a collection or a map, which is no text, fails
     */
    public static String joined(final Object value) {
        if (value != null && isItems(value.getClass())) {
            throw cannotJoinText(value.getClass());
        }
        return String.valueOf(value);
    }

    /**
     * @return whether a value of the static type is an array, a collection or a map, which takes no part in a text
     *     {@code +} on either side
     */
    public static boolean isItems(final Class<?> type) {
        return type != null && (type.isArray() || Collection.class.isAssignableFrom(type)
            || Map.class.isAssignableFrom(type));
    }

    /**
     * @return whether the value may stand left of a {@code +} that joins a text: a text, a number, a character or
     *     null, which Groovy has that {@code +} for (an array, a collection or a map takes no part on either side)
     */
    public static boolean joinsText(final Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Character;
    }

    /**
     * @return whether a value of the static type may stand left of a {@code +} that joins a text: one that
     *     {@link #joinsText}, or one held as a type such a value also is ({@code Object}, {@code Comparable}), which
     *     the run decides; null for a type not known
     */
    public static boolean mayJoinText(final Class<?> type) {
        if (type == null) {
            return true;
        }
        final Class<?> boxed = DslMethods.boxed(type);
        return CharSequence.class.isAssignableFrom(boxed) || Number.class.isAssignableFrom(boxed)
            || boxed == Character.class || boxed.isAssignableFrom(String.class)
            || boxed.isAssignableFrom(Integer.class) || boxed.isAssignableFrom(Character.class);
    }

    /**
     * The failure for a value of the type in a {@code +} that joins a text: on the left anything but a text, a
     * number or a character; on either side an array, a collection or a map, which is no text.
     */
    public static IllegalArgumentException cannotJoinText(final Class<?> type) {
        if (isItems(type)) {
            return new IllegalArgumentException("A " + type.getSimpleName() + " takes no part in a text +: an array, a "
                + "collection or a map is no text; read its elements, or call toString() for its text");
        }
        return new IllegalArgumentException("A " + type.getSimpleName() + " + a string has no meaning in Groovy; "
            + "convert it first, e.g. String.valueOf(...) + ...");
    }

    /**
     * @return the number's exact decimal value, as its text gives it; null for a NaN or an infinity
     */
    public static BigDecimal exact(final Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (isWholeBox(value)) {
            return BigDecimal.valueOf(value.longValue());
        }
        final double d = value.doubleValue();
        try {
            return new BigDecimal(value.toString().trim());
        } catch (final NumberFormatException e) {
            return Double.isNaN(d) || Double.isInfinite(d) ? null : BigDecimal.valueOf(d);
        }
    }

    /**
     * Groovy's BigDecimalMath division, the quotient of whole numbers in every DSL: exact where it terminates, else ten
     * digits beyond the operands' precision, rounded half up to the operands' scale or ten decimals, whichever is more
     * ({@code bytes / 1048576 / 86400} keeps its twenty); a zero divisor fails.
     */
    public static BigDecimal quotient(final BigDecimal left, final BigDecimal right) {
        if (right.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        try {
            return left.divide(right);
        } catch (final ArithmeticException nonTerminating) {
            final BigDecimal result = left.divide(right, new MathContext(
                Math.max(left.precision(), right.precision()) + 10));
            final int scale = Math.max(Math.max(left.scale(), right.scale()), 10);
            return result.scale() > scale ? result.setScale(scale, RoundingMode.HALF_UP) : result;
        }
    }

    /**
     * An integer literal's value, as Groovy reads one: octal after a leading 0 ({@code 010} is 8), else decimal.
     *
     * @param digits the literal's digits alone, no sign nor suffix
     * @param where  the literal and where it stands, for the message
     * @throws IllegalArgumentException for a leading 0 before an 8 or a 9, which Groovy refuses
     */
    public static BigInteger wholeLiteral(final String digits, final String where) {
        if (digits.length() > 1 && digits.charAt(0) == '0') {
            if (digits.indexOf('8') >= 0 || digits.indexOf('9') >= 0) {
                throw new IllegalArgumentException(where + " is no octal number: an integer with a leading 0 is "
                    + "octal, as in Groovy; write it without the 0");
            }
            return new BigInteger(digits, 8);
        }
        return new BigInteger(digits);
    }

    public static boolean isWholeBox(final Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }

    /**
     * The failure for a value a conversion to the type does not take.
     */
    public static IllegalArgumentException cannotConvert(final Object value, final String type) {
        return new IllegalArgumentException(value == null ? "null cannot be converted to " + type
            : describe(value) + " cannot be converted to " + type);
    }

    private static String describe(final Object value) {
        return value == null ? "null" : value.getClass().getName() + " '" + value + "'";
    }
}
