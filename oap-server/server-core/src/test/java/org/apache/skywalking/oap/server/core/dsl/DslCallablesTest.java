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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCallablesTest {

    /** A bean with an instance getter, an instance setter and a static setter. */
    public static final class Bean {
        private String name;

        public String getName() {
            return name;
        }

        public String getPart(final int index) {
            return name.substring(index);
        }

        public void setName(final String name) {
            this.name = name;
        }

        public static void setShared(final String value) {
            // a static setter: never a value's
        }

        public String getJoined(final String... parts) {
            return name + String.join("", parts);
        }
    }

    private final DslCallables list = DslCallables.builder("a test rule", "the guide")
        .methods(Object.class, "toString", "equals")
        .methods(String.class, DslCallables.TEXT_METHODS)
        .methods(Collection.class, "size", "isEmpty")
        .methods(List.class, "get")
        .getters(Bean.class)
        .setters(Bean.class)
        .statics(Integer.class, "parseInt")
        .build();

    @Test
    void aValueCallsWhatItsTypeHierarchyLists() {
        assertTrue(list.allows(String.class, "trim", 0));
        assertTrue(list.allows(ArrayList.class, "size", 0), "a Collection entry is every list's");
        assertTrue(list.allows(ArrayList.class, "get", 1), "and a List entry an ArrayList's");
        assertTrue(list.allows(int.class, "toString", 0), "a primitive as its box; Object's entries are every value's");
        assertFalse(list.allows(String.class, "getBytes", 0));
        assertFalse(list.allows(ArrayList.class, "remove", 1));
        assertFalse(list.allows(Long.class, "intValue", 0));
        assertTrue(list.allows(Long.class, "toString", 0));
        assertFalse(list.allows(Long.class, "toString", 1), "the static Long.toString(long) is no value's method");
    }

    @Test
    void gettersAndInstanceSettersOnly() {
        assertTrue(list.allows(Bean.class, "getName", 0));
        assertTrue(list.allows(Bean.class, "setName", 1));
        assertFalse(list.allows(Bean.class, "getClass", 0), "getClass is no getter");
        assertFalse(list.allows(Bean.class, "setShared", 1), "a static setter stays refused");
        assertTrue(list.allows(Bean.class, "getPart", 1), "a keyed getter reads too");
    }

    @Test
    void staticsAreListedByClass() {
        assertTrue(list.allowsStatic(Integer.class, "parseInt"));
        assertFalse(list.allowsStatic(Integer.class, "toString"));
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> list.requireStatic(Math.class, "max"));
        assertTrue(e.getMessage().contains("not supported in a test rule"), e.getMessage());
        assertTrue(e.getMessage().contains("Integer.parseInt"), e.getMessage());
    }

    @Test
    void theMessageNamesWhatTheValueAllows() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> list.require(String.class, "getBytes", 0));
        assertTrue(e.getMessage().startsWith("String.getBytes(...) is not supported in a test rule"), e.getMessage());
        assertTrue(e.getMessage().contains("trim"), e.getMessage());
        assertTrue(e.getMessage().contains("(see the guide)"), e.getMessage());
    }

    @Test
    void aResolvedMethodIsCheckedAsItsCallIs() throws Exception {
        assertTrue(list.allows(String.class, String.class.getMethod("trim")));
        assertFalse(list.allows(String.class, String.class.getMethod("getBytes")));
        final IllegalArgumentException isStatic = assertThrows(IllegalArgumentException.class,
            () -> list.require(Integer.class, Integer.class.getMethod("toString", int.class)));
        assertTrue(isStatic.getMessage().contains("is a static method, which a test rule does not call through a value"),
            isStatic.getMessage());
        final Method joined = Bean.class.getMethod("getJoined", String[].class);
        assertTrue(list.allows(Bean.class, joined));
        assertFalse(DslCallables.builder("x", "y").getters(Bean.class).refusingVarargs().build()
            .allows(Bean.class, joined), "an engine may refuse variable-length methods");
    }

    @Test
    void onlyListedStaticFieldsAreRead() {
        final DslCallables fields = DslCallables.builder("a test rule", "the guide")
            .staticFields(Integer.class, "MAX_VALUE").build();
        fields.requireStaticField(Integer.class, "MAX_VALUE");
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> fields.requireStaticField(Integer.class, "MIN_VALUE"));
        assertTrue(e.getMessage().contains("The static fields are [Integer.MAX_VALUE]"), e.getMessage());
        assertThrows(IllegalStateException.class,
            () -> DslCallables.builder("x", "y").staticFields(Integer.class, "NOPE"));
    }

    @Test
    void aNameTheClassDoesNotHaveFailsTheList() {
        assertThrows(IllegalStateException.class,
            () -> DslCallables.builder("x", "y").methods(String.class, "trimm"));
        assertThrows(IllegalStateException.class,
            () -> DslCallables.builder("x", "y").statics(String.class, "trim"), "trim is no static method");
    }

    @Test
    void unlistedTypesReadTheirGettersWhenTheEngineSaysSo() {
        final DslCallables open = DslCallables.builder("x", "y").methods(String.class, "trim").gettersOfUnlistedTypes()
            .build();
        assertTrue(open.allows(Bean.class, "getName", 0));
        assertFalse(open.allows(String.class, "getBytes", 0), "a listed type keeps its own list");
        assertEquals(1, open.methodsOf(String.class).size());
    }
}
