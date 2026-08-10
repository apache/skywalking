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

package org.apache.skywalking.oap.server.core.dsl.debug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
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

    @Test
    public void theSameRuleFileIsOneKeyHoweverItsExtensionWasSpelled() {
        // Exactly the two shapes that reached the LAL registry: the boot loader publishes the
        // file name it read from disk, the runtime-rule engine publishes the rule's bare name.
        final RuleKey fromBoot = new RuleKey(Catalog.LAL, "default.yaml", "default");
        final RuleKey fromHotUpdate = new RuleKey(Catalog.LAL, "default", "default");

        assertEquals(fromBoot, fromHotUpdate);
        assertEquals(fromBoot.hashCode(), fromHotUpdate.hashCode());
        assertEquals("default", fromBoot.getName());
        assertEquals(new RuleKey(Catalog.LAL, "default.yml", "default"), fromBoot);
    }

    @Test
    public void oneRegistryEntryNotTwo() {
        // The failure was never an exception — the two keys simply never met, so a hot update
        // left the static binding stranded and an operator on the old spelling toggled a gate
        // belonging to a rule that no longer runs.
        final Map<RuleKey, String> bindings = new ConcurrentHashMap<>();
        bindings.put(new RuleKey(Catalog.LAL, "default.yaml", "default"), "static");
        bindings.put(new RuleKey(Catalog.LAL, "default", "default"), "hot-updated");

        assertEquals(1, bindings.size(), "a hot update must replace the static binding");
        assertEquals("hot-updated",
            bindings.get(new RuleKey(Catalog.LAL, "default.yaml", "default")),
            "the operator-facing spelling must resolve to the live holder");
    }

    @Test
    public void onlyAYamlExtensionIsStripped() {
        // OAL files are core.oal, MAL bundles nest, and a dot in the stem is not an extension.
        assertEquals("core.oal", new RuleKey(Catalog.OAL, "core.oal", "x").getName());
        assertEquals("activemq/activemq-broker",
            new RuleKey(Catalog.OTEL_RULES, "activemq/activemq-broker", "x").getName());
        assertEquals("vm.linux",
            new RuleKey(Catalog.OTEL_RULES, "vm.linux.yaml", "x").getName());
        assertNotEquals(new RuleKey(Catalog.OAL, "core.oal", "x"),
            new RuleKey(Catalog.OAL, "core", "x"));
    }
}
