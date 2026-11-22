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

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DslValuesTest {

    @Test
    public void containersCompareByContentAcrossTheirKinds() {
        assertTrue(DslValues.equal(List.of("a", "b"), "a-b".split("-")), "a list against an array");
        assertTrue(DslValues.equal(new byte[] {65}, new int[] {65}), "elements by value");
        assertTrue(DslValues.equal(List.of(1), List.of(1.0)));
        assertTrue(DslValues.equal(List.of("3"), List.of(3)), "a text element against a number reads as one");
        assertFalse(DslValues.equal(List.of("a", "b"), List.of("b", "a")), "a list keeps its order");
        assertTrue(DslValues.equal(new LinkedHashSet<>(Arrays.asList(1, 2)), new LinkedHashSet<>(Arrays.asList(2L, 1L))),
            "a set by its elements in any order");
        assertTrue(DslValues.equal(Map.of("k", List.of(1)), Map.of("k", new long[] {1})));
        assertFalse(DslValues.equal(Map.of("k", 1), Map.of("j", 1)));
        assertFalse(DslValues.equal(List.of(Double.NaN), List.of(Double.NaN)), "a NaN equals nothing");
        assertFalse(DslValues.equal(Set.of("a"), List.of("a")), "a set is no list");
        assertFalse(DslValues.equal(new LinkedHashSet<>(Arrays.asList(1, 1L)), new LinkedHashSet<>(Arrays.asList(1, 2))),
            "each element of either set is in the other");
        final Map<String, Integer> ab = new LinkedHashMap<>();
        ab.put("a", 1);
        ab.put("b", 2);
        final Map<String, Integer> ba = new LinkedHashMap<>();
        ba.put("b", 2);
        ba.put("a", 1);
        assertTrue(DslValues.equal(ab.values(), ba.values()), "a map's values() has no order of its own");
        assertFalse(DslValues.equal(List.of(1, 1, 2), ab.values()), "each element as often");
    }

    @Test
    void unorderedElementsPairOneToOneWhateverTheOrder() {
        final Map<String, Object> left = new LinkedHashMap<>();
        left.put("x", 1);
        left.put("y", "01");
        final Map<String, Object> right = new LinkedHashMap<>();
        right.put("p", "01");
        right.put("q", "1");
        assertTrue(DslValues.equal(left.values(), right.values()), "1 pairs with \"1\" once \"01\" takes \"01\"");
        right.put("q", "2");
        assertFalse(DslValues.equal(left.values(), right.values()));
    }

    @Test
    void aMapThatRefusesAKeyHoldsNoSuchKey() {
        final Map<String, Integer> nullKey = new HashMap<>();
        nullKey.put(null, 1);
        assertFalse(DslValues.equal(nullKey, Map.of("a", 1)), "Map.of refuses a null key in a lookup");
        assertFalse(DslValues.equal(Map.of("a", 1), nullKey));
        final Map<Object, Integer> sorted = new TreeMap<>(Map.of("a", 1));
        assertFalse(DslValues.equal(Map.of(1, 1), sorted), "a sorted map refuses a key of another type");
        final Map<Object, Object> nested = new HashMap<>();
        nested.put("a", Map.of("b", List.of(1)));
        assertTrue(DslValues.equal(nested, Map.of("a", Map.of("b", List.of(1L)))), "the values compare by content");
    }

    @Test
    public void groovyTruthAndTheTextTruthDifferOnlyForOtherObjects() {
        for (final Object falsy : new Object[] {null, false, "", 0, 0L, 0.0d, Double.NaN, '\0', List.of(), Map.of(),
            new int[0]}) {
            assertFalse(DslValues.isTruthy(falsy), String.valueOf(falsy));
            assertFalse(DslValues.isNotEmpty(falsy), String.valueOf(falsy));
        }
        final Object blankText = new Object() {
            @Override
            public String toString() {
                return "";
            }
        };
        assertTrue(DslValues.isTruthy(blankText), "Groovy: any other object is true");
        assertFalse(DslValues.isNotEmpty(blankText), "LAL and TAL: true when its text is not empty");
        assertFalse(DslValues.isTruthy(Optional.empty()));
    }

    @Test
    public void valuesOrderAsEveryDslOrdersThem() {
        assertEquals(Integer.valueOf(-1), DslValues.order(null, 0));
        assertEquals(Integer.valueOf(-1), DslValues.order("3", 5));
        assertEquals(Integer.valueOf(1), DslValues.order("10", "2"));
        assertTrue(DslValues.order("b", "a") > 0);
        assertEquals(Integer.valueOf(0), DslValues.order('7', 7));
        assertEquals(null, DslValues.order(Double.NaN, "1"));
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DslValues.order("x", 1));
        assertTrue(e.getMessage().contains("reads as no number"), e.getMessage());
        assertFalse(DslValues.ordersByValue(true, false));
        assertTrue(DslValues.ordersByValue(null, 'c'));
    }

    @Test
    public void aContainerHoldingItselfComparesAndPrintsWithoutEnd() {
        final Map<String, Object> self = new LinkedHashMap<>();
        self.put("self", self);
        assertTrue(DslValues.equal(self, self), "a cycle ends equal, as Java's equality ends it");
        final Map<String, Object> other = new LinkedHashMap<>();
        other.put("self", other);
        assertTrue(DslValues.equal(self, other));
        final Map<String, Object> nan = new LinkedHashMap<>();
        nan.put("n", Double.NaN);
        assertFalse(DslValues.equal(nan, nan), "a NaN inside one map still equals nothing");
    }

    @Test
    public void scalarsCompareAsEveryDslDoes() {
        assertTrue(DslValues.equal(3, 3L));
        assertTrue(DslValues.equal("3", 3));
        assertFalse(DslValues.equal("A", 65));
        assertTrue(DslValues.equal('a', "a"));
        assertTrue(DslValues.equal(new StringBuilder("x"), "x"));
        assertTrue(DslValues.equal(-0.0d, 0.0d));
        assertFalse(DslValues.equal(Double.NaN, Double.NaN), "a NaN equals nothing");
        assertTrue(DslValues.equal(null, null));
        assertFalse(DslValues.equal(null, 0));
    }

    @Test
    public void aJsonTreeComparesAsWhatItHolds() {
        assertTrue(DslValues.equal(JsonParser.parseString("[1, \"a\", true]"), Arrays.asList(1L, "a", true)));
        assertTrue(DslValues.equal(JsonParser.parseString("{\"a\": 1.70, \"b\": [2]}"),
            Map.of("a", 1.7, "b", List.of(2))));
        assertTrue(DslValues.equal(JsonParser.parseString("{\"a\": 1}"), JsonParser.parseString("{\"a\": 1.0}")));
        assertFalse(DslValues.equal(JsonParser.parseString("[1, 2]"), JsonParser.parseString("[2, 1]")));
        assertTrue(DslValues.equal(JsonParser.parseString("\"7\""), 7));
        assertTrue(DslValues.equal(JsonParser.parseString("\"x\""), "x"));
        assertFalse(DslValues.equal(JsonNull.INSTANCE, null));
        assertTrue(DslValues.equal(JsonNull.INSTANCE, JsonNull.INSTANCE));
        assertFalse(DslValues.equal(JsonParser.parseString("{\"a\": 1}"), Map.of("a", 2)));
    }
}
