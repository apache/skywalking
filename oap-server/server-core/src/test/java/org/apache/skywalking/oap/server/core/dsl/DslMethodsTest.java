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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DslMethodsTest {

    @Test
    public void anInterfaceAnswersObjectsMethods() throws Exception {
        assertNotNull(DslMethods.find(Map.class, "toString"));
        assertTrue(Arrays.stream(DslMethods.of(List.class)).anyMatch(m -> "equals".equals(m.getName())));
        assertTrue(DslMethods.has(Map.class, "toString", 0));
        assertFalse(DslMethods.has(Map.class, "toString", 1));
    }

    @Test
    public void javasOverloadUnboxesInItsSecondPhase() {
        assertEquals(int.class, DslMethods.overload(String.class, "indexOf", new Class<?>[] {Integer.class})
            .getParameterTypes()[0]);
    }

    @Test
    public void whatEveryOverloadSharesIsKnown() {
        assertEquals(int.class, DslMethods.commonReturnType(String.class, "indexOf", 1));
        assertNull(DslMethods.commonParameterTypes(String.class, "indexOf", 1), "indexOf(int) and indexOf(String)");
        assertArrayEquals(new Class<?>[] {int.class, int.class},
            DslMethods.commonParameterTypes(String.class, "substring", 2));
    }

    @Test
    public void aChainCallTakesTheOverloadItsArgumentsFitBest() throws Exception {
        final List<Method> indexOf = Arrays.asList(String.class.getMethod("indexOf", int.class),
            String.class.getMethod("indexOf", String.class));
        assertEquals(String.class, DslMethods.choose(indexOf, new Class<?>[] {String.class}, null)
            .method.getParameterTypes()[0]);
        assertEquals(int.class, DslMethods.choose(indexOf, new Class<?>[] {Integer.class}, null)
            .method.getParameterTypes()[0]);
        final DslMethods.Choice unknown = DslMethods.choose(indexOf, new Class<?>[] {Object.class}, null);
        assertNotNull(unknown.rival, "a value of unknown type fits both");
        assertEquals(String.class, DslMethods.choose(indexOf, new Class<?>[] {Object.class}, new boolean[] {true})
            .method.getParameterTypes()[0], "a value known to hold text takes the string overload");
        assertNull(DslMethods.choose(indexOf, new Class<?>[] {Boolean.class}, null));
    }
}
