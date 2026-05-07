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

package org.apache.skywalking.oap.server.core.dsldebug;

import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RuleKeyTest {

    @Test
    public void equality_isFieldwise() {
        final RuleKey a = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final RuleKey b = new RuleKey(Catalog.OTEL_RULES, "vm", "cpu");
        final RuleKey c = new RuleKey(Catalog.OTEL_RULES, "vm", "memory");
        final RuleKey d = new RuleKey(Catalog.LOG_MAL_RULES, "vm", "cpu");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    public void requiresAllFields() {
        assertThrows(NullPointerException.class, () -> new RuleKey(null, "vm", "cpu"));
        assertThrows(NullPointerException.class, () -> new RuleKey(Catalog.OTEL_RULES, null, "cpu"));
        assertThrows(NullPointerException.class, () -> new RuleKey(Catalog.OTEL_RULES, "vm", null));
    }
}
