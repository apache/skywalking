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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.analysis.Layer;

/**
 * How generated code converts and compares values: the {@code as} conversions, Javassist's missing autoboxing,
 * conditions over a bare expression, typed {@code Layer} and enum values, and the list a {@code foreach} walks. Not a
 * function a rule calls.
 *
 * <p>The conversions behave as {@code LalRuntimeHelper}'s in {@code log-analyzer} do.
 */
public final class TalValueConversions {
    /**
     * What an {@code as T} answers in a comparison when the value cannot be converted; a comparison with it is false.
     */
    private static final Object CAST_FAILED = new Object();

    private TalValueConversions() {
    }

    // ==================== Comparisons ====================

    /**
     * {@code value as Integer} in a comparison.
     *
     * @param value the value to convert
     * @return the Integer, or a failure marker for a value that is absent or not a whole number of that range
     */
    public static Object asInteger(final Object value) {
        final Object number = asNumber(value);
        if (number instanceof Number) {
            final double d = ((Number) number).doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return ((Number) number).intValue();
            }
        }
        return CAST_FAILED;
    }

    /**
     * {@code value as Long} in a comparison.
     *
     * @param value the value to convert
     * @return the Long, or a failure marker for a value that is absent or not a whole number
     */
    public static Object asLong(final Object value) {
        final Object number = asNumber(value);
        if (number instanceof Long || number instanceof Integer) {
            return ((Number) number).longValue();
        }
        if (number instanceof Double && ((Double) number) == Math.rint((Double) number)) {
            return ((Double) number).longValue();
        }
        return CAST_FAILED;
    }

    /**
     * {@code value as Double} in a comparison.
     *
     * @param value the value to convert
     * @return the Double, or a failure marker for a value that is absent or not a number
     */
    public static Object asDouble(final Object value) {
        final Object number = asNumber(value);
        return number instanceof Number ? (Object) ((Number) number).doubleValue() : CAST_FAILED;
    }

    /**
     * {@code value as Float} in a comparison.
     *
     * @param value the value to convert
     * @return the Float, or a failure marker for a value that is absent or not a number
     */
    public static Object asFloat(final Object value) {
        final Object number = asNumber(value);
        return number instanceof Number ? (Object) ((Number) number).floatValue() : CAST_FAILED;
    }

    /**
     * {@code value as Boolean} in a comparison.
     *
     * @param value the value to convert
     * @return the Boolean, or a failure marker for anything but a Boolean or the text true or false
     */
    public static Object asBoolean(final Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.valueOf(text);
            }
        }
        return CAST_FAILED;
    }

    /**
     * @return a Long or a Double for a number or a text that parses as one, else null
     */
    private static Object asNumber(final Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (final NumberFormatException e) {
                try {
                    return Double.parseDouble(text);
                } catch (final NumberFormatException e2) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * A comparison of two numbers with Java's promotion: as doubles when either is a floating-point number, as
     * longs otherwise.
     *
     * @param left  the left operand
     * @param right the right operand
     * @param op    the operator, the ordinal of {@code CompareOp}: ==, !=, &gt;, &lt;, &gt;=, &lt;=
     * @return the comparison; false when either side is absent or a failed {@code as T}
     */
    public static boolean compareNumbers(final Object left, final Object right, final int op) {
        if (!(left instanceof Number) || !(right instanceof Number)) {
            return false;
        }
        final int sign;
        if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
            final double l = ((Number) left).doubleValue();
            final double r = ((Number) right).doubleValue();
            if (Double.isNaN(l) || Double.isNaN(r)) {
                return op == 1;
            }
            sign = l < r ? -1 : l > r ? 1 : 0;
        } else {
            sign = Long.compare(((Number) left).longValue(), ((Number) right).longValue());
        }
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

    /**
     * {@code ==} or {@code !=} of two values of one kind.
     *
     * @param left  the left operand
     * @param right the right operand
     * @param equal true for {@code ==}
     * @return whether they are equal, or differ; false when either side is a failed {@code as T}
     */
    public static boolean sameValue(final Object left, final Object right, final boolean equal) {
        if (left == CAST_FAILED || right == CAST_FAILED) {
            return false;
        }
        return Objects.equals(left, right) == equal;
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

    public static String toStr(final boolean value) {
        return String.valueOf(value);
    }

    public static String toStr(final Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    public static long toLong(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            return Long.parseLong((String) obj);
        }
        return 0L;
    }

    public static int toInt(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return 0;
    }

    public static double toDouble(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            return Double.parseDouble((String) obj);
        }
        return 0.0d;
    }

    public static float toFloat(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).floatValue();
        }
        if (obj instanceof String) {
            return Float.parseFloat((String) obj);
        }
        return 0.0f;
    }

    public static boolean toBool(final Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return obj != null;
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
     * Truthiness for an {@code if} over a bare expression: null is false, a Boolean speaks for itself, a String
     * parses, anything else present is true.
     *
     * @param obj the value the condition read
     * @return whether the condition holds
     */
    public static boolean isTrue(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return true;
    }

    public static boolean isTrue(final boolean value) {
        return value;
    }

    /**
     * Non-emptiness for an {@code if} over a bare expression, which is how {@code if (!peer)} reads. A boxed
     * boolean is its truth, so {@code if (meta.root)} reads as a condition rather than as the non-empty string
     * {@code "false"}; LAL's helper has no such case, since a log field is text.
     *
     * @param obj the value the condition read
     * @return whether it is present and not the empty string, or true for a {@code Boolean}
     */
    public static boolean isNotEmpty(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return !((String) obj).isEmpty();
        }
        return !obj.toString().isEmpty();
    }

    public static boolean isNotEmpty(final boolean value) {
        return value;
    }

    /**
     * Null-safe {@code ?.toString()}.
     *
     * @param obj the value
     * @return its string form, or null
     */
    public static String toString(final Object obj) {
        return obj == null ? null : obj.toString();
    }

    public static String trim(final Object obj) {
        return obj == null ? null : obj.toString().trim();
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
