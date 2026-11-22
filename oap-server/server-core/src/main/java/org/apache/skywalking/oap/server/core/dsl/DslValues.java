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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How the DSL engines compare and test values: {@code ==} and the orderings the same in MAL, LAL, TAL and the
 * hierarchy rules, and the truth of a value in a condition.
 */
public final class DslValues {
    private static final Object ABSENT = new Object();

    private DslValues() {
    }

    /**
     * Groovy truth, as MAL closures and hierarchy rules test a value: null, {@code false}, an empty text, collection,
     * map, array or iterator, an absent optional, a zero number, NaN and the character 0 are false; any other value is
     * true.
     */
    public static boolean isTruthy(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0;
        }
        if (value instanceof Double || value instanceof Float) {
            final double d = ((Number) value).doubleValue();
            return d != 0.0D && !Double.isNaN(d);
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).signum() != 0;
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).signum() != 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() != 0L;
        }
        if (value instanceof Character) {
            return (Character) value != 0;
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof Iterator) {
            return ((Iterator<?>) value).hasNext();
        }
        if (value instanceof Optional) {
            return ((Optional<?>) value).isPresent();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    /**
     * The truth LAL and TAL test a value by: null, {@code false}, an empty text, collection, map or array, a zero
     * number, NaN and the character 0 are false; a value of another type is true when its text is not empty, so a
     * protobuf message with no field set is false.
     */
    public static boolean isNotEmpty(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0;
        }
        if (value instanceof Number) {
            final double d = ((Number) value).doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        if (value instanceof Character) {
            return (Character) value != 0;
        }
        return !value.toString().isEmpty();
    }

    /**
     * @return whether {@link #order} orders the two values: each a null, a number, a text or a character
     */
    public static boolean ordersByValue(final Object left, final Object right) {
        return valueLike(left) && valueLike(right);
    }

    private static boolean valueLike(final Object value) {
        return value == null || value instanceof Number || value instanceof CharSequence || value instanceof Character;
    }

    /**
     * {@code <}, {@code <=}, {@code >} and {@code >=} as every DSL orders values: a null below every value; a
     * character as its one-character string; numbers by value ({@link DslNumbers#order}); a text against a number as
     * the number it reads as, trimmed; two texts as numbers when both read as one, else as text. An engine orders
     * other pairs (two booleans, two comparables) its own way.
     *
     * @return the order, null when a NaN stands on either side, which has none
     * @throws IllegalArgumentException for a text against a number that reads as none, and for a pair
     *                                  {@link #ordersByValue} refuses
     */
    public static Integer order(final Object leftValue, final Object rightValue) {
        if (leftValue == null || rightValue == null) {
            return leftValue == rightValue ? 0 : leftValue == null ? -1 : 1;
        }
        final Object left = leftValue instanceof Character ? leftValue.toString() : leftValue;
        final Object right = rightValue instanceof Character ? rightValue.toString() : rightValue;
        if (left instanceof Number && right instanceof Number) {
            return DslNumbers.order((Number) left, (Number) right);
        }
        if (left instanceof CharSequence && right instanceof CharSequence) {
            final BigDecimal l = DslNumbers.numberIn((CharSequence) left);
            final BigDecimal r = DslNumbers.numberIn((CharSequence) right);
            return l != null && r != null ? l.compareTo(r) : left.toString().compareTo(right.toString());
        }
        if (left instanceof CharSequence && right instanceof Number) {
            return DslNumbers.order(numberToOrder((CharSequence) left, rightValue), (Number) right);
        }
        if (left instanceof Number && right instanceof CharSequence) {
            return DslNumbers.order((Number) left, numberToOrder((CharSequence) right, leftValue));
        }
        throw new IllegalArgumentException("Cannot order " + describe(leftValue) + " against " + describe(rightValue));
    }

    private static BigDecimal numberToOrder(final CharSequence text, final Object number) {
        final BigDecimal value = DslNumbers.numberIn(text);
        if (value == null) {
            throw new IllegalArgumentException(
                "Cannot order the text '" + text + "' against " + describe(number) + ": it reads as no number");
        }
        return value;
    }

    private static String describe(final Object value) {
        return value.getClass().getName() + " '" + value + "'";
    }

    /**
     * {@code ==}: numbers by value whatever their types ({@code 3 == 3L}), a NaN equal to nothing, as Java compares it;
     * a text or a character against a number as the number it reads as ({@code "3" == 3}), unequal when it reads as
     * none; a character as its one-character string; texts by content; lists and arrays element by element in order
     * (a list against an array too), sets by their elements in any order, a collection with no order of its own (a
     * map's values()) by its elements and how often each occurs, in any order; maps by their keys, matched as the map
     * itself looks a key up, and each key's value; anything else by {@code equals}. A null equals only a null. A JSON
     * tree compares as what it holds: an array as a list, an object as a map, a primitive as its number, text or
     * boolean; a JSON null is a value, not null, as a rule's null check has always read it.
     */
    public static boolean equal(final Object left, final Object right) {
        return equal(left, right, new ArrayDeque<>());
    }

    /**
     * @return whether {@link #equal} of a value of the static type can differ from its {@code equals}: the value can be
     *     an array, a collection, a map or a JSON tree
     */
    public static boolean comparesByContent(final Class<?> type) {
        return DslStrings.mayHoldItems(type) || JsonElement.class.isAssignableFrom(type);
    }

    /**
     * @param comparing the container pairs being compared further up: one met again inside itself (a map holding
     *                  itself) is taken as equal there, as Java's own equality ends such a cycle
     */
    private static boolean equal(final Object left, final Object right, final Deque<Object[]> comparing) {
        final Object l = scalar(left);
        final Object r = scalar(right);
        if (l == null || r == null) {
            return l == r;
        }
        if (l instanceof Number && r instanceof Number) {
            return !DslNumbers.isUnordered((Number) l, (Number) r) && DslNumbers.compare((Number) l, (Number) r) == 0;
        }
        if (l instanceof Number && r instanceof CharSequence || l instanceof CharSequence && r instanceof Number) {
            final BigDecimal read = DslNumbers.numberIn((CharSequence) (l instanceof CharSequence ? l : r));
            final Number number = (Number) (l instanceof Number ? l : r);
            return read != null && !DslNumbers.isUnordered(read, number) && DslNumbers.compare(read, number) == 0;
        }
        if (l instanceof CharSequence && r instanceof CharSequence) {
            return l.toString().equals(r.toString());
        }
        if (isContainer(l) && isContainer(r)) {
            for (final Object[] pair : comparing) {
                if (pair[0] == l && pair[1] == r) {
                    return true;
                }
            }
            comparing.push(new Object[] {l, r});
            try {
                return sameContainers(l, r, comparing);
            } finally {
                comparing.pop();
            }
        }
        return l.equals(r);
    }

    /**
     * @return a character as its one-character string, a JSON primitive as its value
     */
    private static Object scalar(final Object value) {
        if (value instanceof Character) {
            return value.toString();
        }
        if (value instanceof JsonPrimitive) {
            final JsonPrimitive primitive = (JsonPrimitive) value;
            return primitive.isNumber() ? primitive.getAsNumber()
                : primitive.isBoolean() ? (Object) primitive.getAsBoolean() : primitive.getAsString();
        }
        return value;
    }

    private static boolean isContainer(final Object value) {
        return value instanceof Collection || value instanceof Map || value.getClass().isArray()
            || value instanceof JsonArray || value instanceof JsonObject;
    }

    private static boolean sameContainers(final Object l, final Object r, final Deque<Object[]> comparing) {
        if (l instanceof Set && r instanceof Set) {
            return sameElements((Set<?>) l, (Set<?>) r, comparing);
        }
        final List<?> leftItems = ordered(l);
        final List<?> rightItems = ordered(r);
        if (leftItems != null && rightItems != null && (unordered(l) || unordered(r))) {
            return sameOccurrences(leftItems, rightItems, comparing);
        }
        if (leftItems != null && rightItems != null) {
            if (leftItems.size() != rightItems.size()) {
                return false;
            }
            for (int i = 0; i < leftItems.size(); i++) {
                if (!equal(leftItems.get(i), rightItems.get(i), comparing)) {
                    return false;
                }
            }
            return true;
        }
        final Map<?, ?> leftMap = asMap(l);
        final Map<?, ?> rightMap = asMap(r);
        if (leftMap != null && rightMap != null) {
            return sameEntries(leftMap, rightMap, comparing);
        }
        return false;
    }

    private static Map<?, ?> asMap(final Object value) {
        if (value instanceof Map) {
            return (Map<?, ?>) value;
        }
        if (value instanceof JsonObject) {
            final Map<String, JsonElement> entries = new LinkedHashMap<>();
            for (final Map.Entry<String, JsonElement> entry : ((JsonObject) value).entrySet()) {
                entries.put(entry.getKey(), entry.getValue());
            }
            return entries;
        }
        return null;
    }

    /**
     * Each element of either set equals one of the other's: a set holding 1 and 1L is not {1, 2}.
     */
    private static boolean sameElements(final Set<?> left, final Set<?> right, final Deque<Object[]> comparing) {
        if (left.size() != right.size()) {
            return false;
        }
        for (final Object item : left) {
            if (right.stream().noneMatch(other -> equal(item, other, comparing))) {
                return false;
            }
        }
        for (final Object item : right) {
            if (left.stream().noneMatch(other -> equal(item, other, comparing))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The same elements, each as often, in any order: every element paired with an equal one of the other side, one
     * to one. The pairing may revisit an earlier choice, as content equality is not transitive (1 equals "1" and "01").
     */
    private static boolean sameOccurrences(final List<?> left, final List<?> right, final Deque<Object[]> comparing) {
        final int n = left.size();
        if (n != right.size()) {
            return false;
        }
        final boolean[][] equalAt = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                equalAt[i][j] = equal(left.get(i), right.get(j), comparing);
            }
        }
        final int[] pairedWith = new int[n];
        Arrays.fill(pairedWith, -1);
        for (int i = 0; i < n; i++) {
            if (!pair(i, equalAt, pairedWith, new boolean[n])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pairs the left element {@code i} with a free equal right element, moving an earlier pairing where that frees one.
     */
    private static boolean pair(final int i, final boolean[][] equalAt, final int[] pairedWith, final boolean[] tried) {
        for (int j = 0; j < pairedWith.length; j++) {
            if (equalAt[i][j] && !tried[j]) {
                tried[j] = true;
                if (pairedWith[j] < 0 || pair(pairedWith[j], equalAt, pairedWith, tried)) {
                    pairedWith[j] = i;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return a collection that is neither a list nor a set, such as a map's values(): no order of its own
     */
    private static boolean unordered(final Object value) {
        return value instanceof Collection && !(value instanceof List) && !(value instanceof Set);
    }

    private static boolean sameEntries(final Map<?, ?> left, final Map<?, ?> right, final Deque<Object[]> comparing) {
        if (left.size() != right.size()) {
            return false;
        }
        for (final Map.Entry<?, ?> entry : left.entrySet()) {
            final Object other = valueAt(right, entry.getKey());
            if (other == ABSENT || !equal(entry.getValue(), other, comparing)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the map's value for the key, or {@link #ABSENT}: a map that refuses the key in a lookup ({@code Map.of}
     *     a null, a sorted map a key of another type) holds no such key
     */
    private static Object valueAt(final Map<?, ?> map, final Object key) {
        try {
            return map.containsKey(key) ? map.get(key) : ABSENT;
        } catch (final NullPointerException | ClassCastException refused) {
            return ABSENT;
        }
    }

    /**
     * @return a list, an array or a JSON array, or a collection other than a set, as its items in order; else null
     */
    private static List<?> ordered(final Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value instanceof JsonArray) {
            final List<Object> items = new ArrayList<>();
            ((JsonArray) value).forEach(items::add);
            return items;
        }
        if (value instanceof Collection && !(value instanceof Set)) {
            return new ArrayList<>((Collection<?>) value);
        }
        if (value.getClass().isArray()) {
            final List<Object> items = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        return null;
    }
}
