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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnownServicesTest {
    private final KnownServices known = new KnownServices();

    @Test
    void anUngroupedServiceIsNamedByItsWholeName() {
        known.record("cart");
        assertEquals("cart", known.nameOf("cart"));
        assertNull(known.nameOf("ca"));
    }

    @Test
    void aGroupedServiceIsNamedByItsShortName() {
        known.record("shop::cart");
        assertEquals("shop::cart", known.nameOf("cart"));
        assertEquals("shop::cart", known.nameOf("shop::cart"));
        assertFalse(known.contains("cart"), "the short name is a way to find the service, not a service");
    }

    @Test
    void aShortNameTwoGroupsShareNamesNeither() {
        known.record("shop::cart");
        known.record("outlet::cart");
        assertNull(known.nameOf("cart"), "the host cannot say which group's cart was dialed");
        assertEquals("shop::cart", known.nameOf("shop::cart"));
    }

    @Test
    void anUngroupedServiceWinsOverAGroupedOneOfTheSameShortName() {
        known.record("shop::cart");
        known.record("cart");
        assertEquals("cart", known.nameOf("cart"));
    }

    @Test
    void aNameWithNothingAfterTheSeparatorHasNoShortName() {
        known.record("shop::");
        assertNull(known.nameOf(""));
        assertEquals("shop::", known.nameOf("shop::"));
    }
}
