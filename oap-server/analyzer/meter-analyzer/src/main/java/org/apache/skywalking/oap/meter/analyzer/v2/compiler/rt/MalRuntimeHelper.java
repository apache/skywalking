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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslValues;

/**
 * Static helper methods called by v2-generated {@code MalExpression} classes.
 * Keeps new runtime behaviour in the v2 compiler package, avoiding modifications
 * to the shared {@link SampleFamily} class.
 */
public final class MalRuntimeHelper {

    private MalRuntimeHelper() {
    }

    /**
     * Groovy regex match ({@code =~}): returns a {@code String[][]} where each row is
     * one match with group 0 (full match) and capture groups 1..N.
     * Returns an empty array (a false, size-0 matcher) when nothing matches, so Groovy truth
     * ({@code matcher ? matcher[0][1] : "unknown"}) and {@code matcher.size()} work as in Groovy.
     */
    public static String[][] regexMatch(final String input, final String regex) {
        if (input == null) {
            return new String[0][];
        }
        final Matcher m = Pattern.compile(regex).matcher(input);
        final List<String[]> rows = new ArrayList<>();
        while (m.find()) {
            final String[] row = new String[m.groupCount() + 1];
            for (int i = 0; i <= m.groupCount(); i++) {
                row[i] = m.group(i);
            }
            rows.add(row);
        }
        return rows.toArray(new String[0][]);
    }

    /**
     * Groovy indexing: a negative index counts from the end ({@code parts[-1]} is the last).
     */
    public static String at(final String[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static String[] at(final String[][] array, final int index) {
        // the matches of a regex: a match that is not there fails, as in Groovy; a rule checks first (if (m) ...)
        final int i = index < 0 ? array.length + index : index;
        if (i < 0 || i >= array.length) {
            throw new IndexOutOfBoundsException("The pattern matched " + array.length + " time(s), so match " + index
                + " is not there; check the match first, e.g. if (m) { ... } or m ? m[0][1] : 'default'");
        }
        return array[i];
    }

    /**
     * An index known only at run time: an Integer, Short or Byte, as Groovy's getAt takes on an array.
     */
    public static int index(final Object value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("No index: " + (value == null ? "null" : value.getClass().getSimpleName()
            + " " + value) + "; convert it first, e.g. as Integer");
    }

    /**
     * Groovy's index on a value held as Object (a def reassigned another type): an array, a list or a string by its
     * runtime type, negative from the end.
     */
    public static Object at(final Object value, final int index) {
        if (value instanceof Object[]) {
            return at((Object[]) value, index);
        }
        if (value instanceof List) {
            // Groovy: past the end is null; before the start (a negative index beyond the size) fails
            final List<?> list = (List<?>) value;
            final int i = index < 0 ? list.size() + index : index;
            if (i < 0) {
                throw new IndexOutOfBoundsException("Index " + index + " is before the start of a list of " + list.size());
            }
            return i < list.size() ? list.get(i) : null;
        }
        if (value instanceof CharSequence) {
            final CharSequence text = (CharSequence) value;
            return String.valueOf(text.charAt(index < 0 ? text.length() + index : index));
        }
        if (value != null && value.getClass().isArray()) {
            return Array.get(value, index < 0 ? Array.getLength(value) + index : index);
        }
        throw new IllegalArgumentException("Cannot index " + (value == null ? "null" : value.getClass().getName()));
    }

    /**
     * A string's character, as the one-character String Groovy gives, negative from the end.
     */
    public static String at(final String text, final int index) {
        return String.valueOf(text.charAt(index < 0 ? text.length() + index : index));
    }

    public static Object at(final Object[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static byte at(final byte[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static short at(final short[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static int at(final int[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static long at(final long[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static float at(final float[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static double at(final double[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static char at(final char[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    public static boolean at(final boolean[] array, final int index) {
        return array[index < 0 ? array.length + index : index];
    }

    /**
     * The orderings of operands the generated code boxes first, as every DSL orders values
     * ({@link DslValues#order}), a NaN on either side false, as Java compares it.
     */
    public static boolean gt(final Object left, final Object right) {
        final Integer order = order(left, right);
        return order != null && order > 0;
    }

    public static boolean gte(final Object left, final Object right) {
        final Integer order = order(left, right);
        return order != null && order >= 0;
    }

    public static boolean lt(final Object left, final Object right) {
        final Integer order = order(left, right);
        return order != null && order < 0;
    }

    public static boolean lte(final Object left, final Object right) {
        final Integer order = order(left, right);
        return order != null && order <= 0;
    }

    /**
     * @return the order of two values: by {@link DslValues#order} where it orders them, else null lowest and two
     *     comparables of one class by compareTo; null when a NaN has none
     * @throws IllegalArgumentException for any other pair
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Integer order(final Object leftValue, final Object rightValue) {
        if (DslValues.ordersByValue(leftValue, rightValue)) {
            return DslValues.order(leftValue, rightValue);
        }
        if (leftValue == null || rightValue == null) {
            return leftValue == rightValue ? 0 : leftValue == null ? -1 : 1;
        }
        // a character is the one-character string it is
        final Object left = leftValue instanceof Character ? leftValue.toString() : leftValue;
        final Object right = rightValue instanceof Character ? rightValue.toString() : rightValue;
        if (left instanceof Comparable && left.getClass() == right.getClass()) {
            return ((Comparable) left).compareTo(right);
        }
        throw new IllegalArgumentException("Cannot order a " + left.getClass().getName() + " against a "
            + right.getClass().getName());
    }

    /**
     * Groovy's {@code +} on values the closure does not type statically: a String on either side joins
     * ({@link #concatLeft}), numbers add by Groovy's arithmetic (an Integer sum stays an Integer and wraps, a Long a
     * Long, a Double or Float makes a Double), a Character counts by its code.
     */
    public static Object plus(final Object left, final Object right) {
        if (left instanceof String || right instanceof String) {
            return concatLeft(left) + concatRight(right);
        }
        requireNoContainer(left);
        return DslNumbers.arithmetic(number(left), number(right), '+');
    }

    public static Object minus(final Object left, final Object right) {
        return DslNumbers.arithmetic(number(left), number(right), '-');
    }

    public static Object multiply(final Object left, final Object right) {
        return DslNumbers.arithmetic(number(left), number(right), '*');
    }

    /**
     * Groovy's {@code /}: with a Double or Float, the quotient in double; otherwise the BigDecimal quotient Groovy
     * computes ({@link DslNumbers#quotient}).
     */
    public static Object divide(final Object left, final Object right) {
        final Number l = number(left);
        final Number r = number(right);
        if (DslNumbers.isFloating(l) || DslNumbers.isFloating(r)) {
            return l.doubleValue() / r.doubleValue();
        }
        return DslNumbers.quotient(DslNumbers.exact(l), DslNumbers.exact(r));
    }

    /**
     * Two whole numbers divided: the BigDecimal quotient Groovy computes (7 / 2 is 3.5). Narrowed into an int or long,
     * the quotient truncates (ns / 1000000 loses no digit); into an int it wraps, as Groovy's intValue() does.
     */
    public static BigDecimal quotient(final long left, final long right) {
        return DslNumbers.quotient(BigDecimal.valueOf(left), BigDecimal.valueOf(right));
    }

    /**
     * Groovy's unary minus on a value the closure does not type statically: the number's own type (a Float stays a
     * Float, -0.0 is kept).
     */
    public static Object negate(final Object value) {
        final Number n = number(value);
        if (n instanceof Double) {
            return -n.doubleValue();
        }
        if (n instanceof Float) {
            return -n.floatValue();
        }
        if (n instanceof Long) {
            return -n.longValue();
        }
        if (n instanceof BigDecimal) {
            return ((BigDecimal) n).negate();
        }
        if (n instanceof BigInteger) {
            return ((BigInteger) n).negate();
        }
        if (n instanceof Byte) {
            return (byte) -n.byteValue();
        }
        if (n instanceof Short) {
            return (short) -n.shortValue();
        }
        return -n.intValue();
    }

    /**
     * A scalar quotient outside closures (time() / n): a zero divisor throws, as Groovy throws dividing whole numbers
     * or decimals by zero.
     */
    public static double scalarDivide(final double left, final double right) {
        if (right == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return left / right;
    }

    /**
     * A value taking part in arithmetic or assigned to a declared number: a number, or a Character by its code.
     */
    public static Number number(final Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        if (value instanceof Character) {
            return (int) (Character) value;
        }
        throw new IllegalArgumentException("A number is needed here, not "
            + (value == null ? "null" : value.getClass().getSimpleName() + " " + gstring(value)));
    }

    /**
     * A map that becomes labels (the map a tag or instance closure returns): a number or boolean value is
     * written as its string; any other non-string value is refused. The map itself is returned when every value is
     * already a string.
     */
    private static Map stringLabels(final Object map) {
        if (!(map instanceof Map)) {
            throw new IllegalArgumentException("Labels are a map, not "
                + (map == null ? "null" : map.getClass().getName()));
        }
        boolean strings = true;
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
            labelName(entry.getKey());
            if (entry.getValue() != null && !(entry.getValue() instanceof String)) {
                strings = false;
            }
        }
        if (strings) {
            return (Map) map;
        }
        final Map<Object, Object> converted = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
            converted.put(entry.getKey(), labelValue(entry.getValue()));
        }
        return converted;
    }

    /**
     * A label's name, which is a string.
     */
    public static String labelName(final Object key) {
        if (key instanceof String) {
            return (String) key;
        }
        throw new IllegalArgumentException("A label's name is a string, not "
            + (key == null ? "null" : key.getClass().getSimpleName() + " " + key));
    }

    /**
     * Arrays.asList's arguments, as Java and Groovy pass varargs: a single array is the arguments.
     */
    public static Object[] varargs(final Object[] arguments) {
        return arguments.length == 1 && arguments[0] instanceof Object[] ? (Object[]) arguments[0] : arguments;
    }

    /**
     * A value written into the labels: a string as it is, a number or boolean as its string (443 is "443"), null kept;
     * any other value is refused, as the labels hold strings.
     */
    public static String labelValue(final Object value) {
        if (value == null || value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value.toString();
        }
        throw new IllegalArgumentException("A label value is a string; a number, boolean or character is written as its "
            + "string, a " + value.getClass().getSimpleName() + " is not");
    }

    /**
     * The left operand of a + that joins a text, as Groovy prints it: a String, a number, a character (the
     * one-character string it is) or null, which Groovy has that + for; any other value fails.
     */
    public static String concatLeft(final Object left) {
        if (left == null || left instanceof String || left instanceof Number || left instanceof Character) {
            return gstring(left);
        }
        if (left instanceof Boolean) {
            throw new IllegalArgumentException("A boolean + a string has no meaning in Groovy; convert it first, e.g. "
                + "String.valueOf(...) + ...");
        }
        requireNoContainer(left);
        throw new IllegalArgumentException("A " + left.getClass().getSimpleName() + " + a string has no meaning in "
            + "Groovy; convert it first, e.g. String.valueOf(...) + ...");
    }

    /**
     * A value joined after a text, as Groovy prints it; an array, a collection or a map, which is no text, fails.
     */
    public static String concatRight(final Object right) {
        if (right != null && DslNumbers.isItems(right.getClass())) {
            throw DslNumbers.cannotJoinText(right.getClass());
        }
        return gstring(right);
    }

    private static void requireNoContainer(final Object left) {
        if (left != null && (left.getClass().isArray() || left instanceof Collection || left instanceof Map)) {
            throw new IllegalArgumentException("+ on " + left.getClass().getSimpleName()
                + " (Groovy appends or merges) is not supported in a closure");
        }
    }

    /**
     * A value assigned to a char, as Groovy assigns it: a character, a one-character string ('0'), a number's code.
     */
    public static char toChar(final Object value) {
        if (value instanceof Character) {
            return (Character) value;
        }
        if (value instanceof CharSequence && ((CharSequence) value).length() == 1) {
            return ((CharSequence) value).charAt(0);
        }
        if (value instanceof Number) {
            return (char) ((Number) value).intValue();
        }
        throw new IllegalArgumentException("A char takes a character, a one-character string or a number, not "
            + (value == null ? "null" : value.getClass().getSimpleName() + " " + gstring(value)));
    }

    /**
     * Groovy's conversion of a value assigned to a String: its text as Groovy prints it, null kept.
     */
    public static String asString(final Object value) {
        return value == null ? null : gstring(value);
    }

    /**
     * An explicit {@code value.toString()}: as {@link #gstring} prints it, a null receiver failing, as {@code toString()}
     * on null always did.
     */
    public static String text(final Object value) {
        if (value == null) {
            throw new NullPointerException("Cannot invoke toString() on null");
        }
        return gstring(value);
    }

    /**
     * An interpolated {@code ${...}} value as Groovy prints it: {@code [a, b]} for an array or a list, {@code [k:v]}
     * for a map, {@code null} for null.
     */
    public static String gstring(final Object value) {
        return gstring(value, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * @param printing the containers being printed further up: one met again inside itself prints as Groovy prints
     *                 it, {@code (this Map)} or {@code (this Collection)}
     */
    private static String gstring(final Object value, final Set<Object> printing) {
        if (value == null) {
            return "null";
        }
        if (printing.contains(value)) {
            return value instanceof Map ? "(this Map)" : "(this Collection)";
        }
        if (value instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                return "[:]";
            }
            printing.add(value);
            final StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(gstring(e.getKey(), printing)).append(':').append(gstring(e.getValue(), printing));
            }
            printing.remove(value);
            return sb.append(']').toString();
        }
        if (value instanceof char[]) {
            return new String((char[]) value);
        }
        if (value instanceof Collection || value.getClass().isArray()) {
            printing.add(value);
            final StringBuilder sb = new StringBuilder("[");
            if (value instanceof Collection) {
                int i = 0;
                for (final Object o : (Collection<?>) value) {
                    sb.append(i++ > 0 ? ", " : "").append(gstring(o, printing));
                }
            } else {
                for (int i = 0; i < Array.getLength(value); i++) {
                    sb.append(i > 0 ? ", " : "").append(gstring(Array.get(value, i), printing));
                }
            }
            printing.remove(value);
            return sb.append(']').toString();
        }
        return value.toString();
    }

    /**
     * What a {@code tag} closure gives: the map it returns, as Groovy's {@code tag} uses it, else the labels.
     */
    public static Map tagResult(final Object value, final Map labels) {
        return value instanceof Map ? stringLabels(value) : labels;
    }

    /**
     * What an {@code instance} properties closure gives: the map it returns, or none for null. Anything else is a
     * {@code ClassCastException} under Groovy too.
     */
    public static Map propertiesResult(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return stringLabels(value);
        }
        throw new IllegalStateException(
            "An instance properties closure returned a " + value.getClass().getName() + ", not a map");
    }

    /**
     * A closure map literal: keys and values alternating. Unlike {@code Map.of}, a missing label's null value is kept,
     * as Groovy keeps it, and any number of entries is taken.
     */
    public static Map<String, Object> mapOf(final Object[] keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /**
     * Reverse division: computes {@code numerator / v} for each sample value {@code v}.
     * Used by generated code for {@code Number / SampleFamily} expressions.
     */
    public static SampleFamily divReverse(final double numerator,
                                          final SampleFamily sf) {
        if (sf == SampleFamily.EMPTY) {
            return SampleFamily.EMPTY;
        }
        final Sample[] original = sf.samples;
        final Sample[] result = new Sample[original.length];
        for (int i = 0; i < original.length; i++) {
            result[i] = original[i].toBuilder()
                .value(numerator / original[i].getValue())
                .build();
        }
        return SampleFamilyBuilder.newBuilder(result).build();
    }
}
