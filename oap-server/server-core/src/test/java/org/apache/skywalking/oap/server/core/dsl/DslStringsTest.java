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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DslStringsTest {

    @Test
    public void textWritesContainersByTheirContentAtAnyDepth() {
        assertEquals("[a, b]", DslStrings.text(new String[] {"a", "b"}));
        assertEquals("[[1, 2], x]", DslStrings.text(Arrays.asList(new int[] {1, 2}, "x")));
        assertEquals("{k=[v]}", DslStrings.text(Map.of("k", new String[] {"v"})));
        assertEquals("[null]", DslStrings.text(Arrays.asList((Object) null)));
        assertThrows(NullPointerException.class, () -> DslStrings.text(null), "toString() on null fails");
    }

    @Test
    public void textEndsAContainerThatHoldsItself() {
        final Map<String, Object> self = new LinkedHashMap<>();
        self.put("self", self);
        assertEquals("{self=(this Map)}", DslStrings.text(self));
    }

    @Test
    public void anInterpolationIsADollarBeforeABraceOrAName() {
        assertTrue(DslStrings.interpolationAt("${a}", 0));
        assertTrue(DslStrings.interpolationAt("$name", 0));
        assertFalse(DslStrings.interpolationAt("$2", 0));
        assertFalse(DslStrings.interpolationAt("US$", 2));
        assertEquals(4, DslStrings.firstInterpolation("a-b $x"));
        assertEquals(-1, DslStrings.firstInterpolation("\\$x and $2/$1"));
    }

    @Test
    public void whatMayHoldAnArrayACollectionOrAMap() {
        assertTrue(DslStrings.mayHoldItems(String[].class));
        assertTrue(DslStrings.mayHoldItems(List.class));
        assertTrue(DslStrings.mayHoldItems(Object.class));
        assertTrue(DslStrings.mayHoldItems(Serializable.class));
        assertTrue(DslStrings.mayHoldItems(Iterable.class));
        assertFalse(DslStrings.mayHoldItems(String.class));
        assertFalse(DslStrings.mayHoldItems(Integer.class));
        assertTrue(DslStrings.mayHoldItems(Collection.class));
    }
}
