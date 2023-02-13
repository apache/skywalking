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

package org.apache.skywalking.oap.server.library.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BooleanUtilsTest {

    @Test
    public void testValueToBoolean() {
        assertEquals(1, BooleanUtils.booleanToValue(true));
        assertEquals(0, BooleanUtils.booleanToValue(false));
    }

    @Test
    public void testBooleanToValue() {
        assertTrue(BooleanUtils.valueToBoolean(1));
        assertFalse(BooleanUtils.valueToBoolean(0));
    }

    @Test
    public void shouldThrowIfValueIsNotZeroOrOne() {
        assertThrows(RuntimeException.class, () -> BooleanUtils.valueToBoolean(123));
    }
}
