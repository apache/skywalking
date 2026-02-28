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

package org.apache.skywalking.oap.server.core.config;

import java.util.Map;
import java.util.function.BiFunction;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaHierarchyRuleProviderTest {

    private JavaHierarchyRuleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JavaHierarchyRuleProvider();
    }

    private Service svc(final String name, final String shortName) {
        final Service s = new Service();
        s.setName(name);
        s.setShortName(shortName);
        return s;
    }

    // ---- name rule ----

    @Test
    void nameRuleMatches() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("name", "{ (u, l) -> u.name == l.name }"));
        assertTrue(rules.get("name").apply(svc("svc", "svc"), svc("svc", "svc")));
    }

    @Test
    void nameRuleDoesNotMatch() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("name", "ignored"));
        assertFalse(rules.get("name").apply(svc("svc", "svc"), svc("other", "other")));
    }

    // ---- short-name rule ----

    @Test
    void shortNameRuleMatches() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("short-name", "ignored"));
        assertTrue(rules.get("short-name").apply(svc("a", "svc"), svc("b", "svc")));
    }

    @Test
    void shortNameRuleDoesNotMatch() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("short-name", "ignored"));
        assertFalse(rules.get("short-name").apply(svc("a", "svc1"), svc("b", "svc2")));
    }

    // ---- lower-short-name-remove-ns rule ----

    @Test
    void lowerShortNameRemoveNsMatches() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-remove-ns", "ignored"));
        // l.shortName = "svc.namespace", u.shortName = "svc"
        assertTrue(rules.get("lower-short-name-remove-ns")
            .apply(svc("a", "svc"), svc("b", "svc.namespace")));
    }

    @Test
    void lowerShortNameRemoveNsNoDot() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-remove-ns", "ignored"));
        assertFalse(rules.get("lower-short-name-remove-ns")
            .apply(svc("a", "svc"), svc("b", "svc")));
    }

    @Test
    void lowerShortNameRemoveNsMismatch() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-remove-ns", "ignored"));
        assertFalse(rules.get("lower-short-name-remove-ns")
            .apply(svc("a", "other"), svc("b", "svc.namespace")));
    }

    // ---- lower-short-name-with-fqdn rule ----

    @Test
    void lowerShortNameWithFqdnMatches() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-with-fqdn", "ignored"));
        // u.shortName = "db:3306", l.shortName = "db" -> "db" == "db.svc.cluster.local"? no
        // u.shortName = "db:3306", l.shortName should match: u prefix = "db", l + fqdn = "db.svc.cluster.local"
        assertTrue(rules.get("lower-short-name-with-fqdn")
            .apply(svc("a", "db.svc.cluster.local:3306"), svc("b", "db")));
    }

    @Test
    void lowerShortNameWithFqdnNoColon() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-with-fqdn", "ignored"));
        assertFalse(rules.get("lower-short-name-with-fqdn")
            .apply(svc("a", "db"), svc("b", "db")));
    }

    @Test
    void lowerShortNameWithFqdnWrongSuffix() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of("lower-short-name-with-fqdn", "ignored"));
        assertFalse(rules.get("lower-short-name-with-fqdn")
            .apply(svc("a", "db:3306"), svc("b", "other")));
    }

    // ---- unknown rule ----

    @Test
    void unknownRuleThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.buildRules(Map.of("unknown-rule", "ignored")));
    }

    // ---- builds all 4 rules ----

    @Test
    void buildsAllFourRules() {
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(Map.of(
                "name", "ignored",
                "short-name", "ignored",
                "lower-short-name-remove-ns", "ignored",
                "lower-short-name-with-fqdn", "ignored"
            ));
        assertEquals(4, rules.size());
    }
}
