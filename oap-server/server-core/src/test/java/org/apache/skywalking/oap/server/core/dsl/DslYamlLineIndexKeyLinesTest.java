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

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hierarchy keys its rules under a MAPPING ({@code auto-matching-rules: {name: expr}}) rather than
 * a sequence, so its lines cannot be matched positionally the way every other DSL's are.
 *
 * <p>Without this, {@code keyLines} returning an empty map would leave the hierarchy tests green:
 * they inject a coordinate into the generator directly, so they never observe what the loader
 * resolved.
 */
class DslYamlLineIndexKeyLinesTest {

    private static final String YAML =
        "hierarchy:\n"                                   // 1
            + "  SERVICE:\n"                             // 2
            + "    K8S_SERVICE: lower-short-name\n"      // 3
            + "auto-matching-rules:\n"                   // 4
            + "  lower-short-name: |\n"                  // 5
            + "    { u, l -> u.name == l.shortName }\n"  // 6
            + "  same-namespace: |\n"                    // 7
            + "    { u, l -> u.namespace == l.namespace }\n"; // 8

    @Test
    void eachRuleKeyResolvesToItsOwnLine() {
        final Map<String, Integer> lines = DslYamlLineIndex.keyLines(YAML, "auto-matching-rules");

        assertEquals(2, lines.size());
        assertEquals(5, lines.get("lower-short-name"));
        assertEquals(7, lines.get("same-namespace"),
            "the second rule must not be shifted by the first rule's multi-line block scalar");
        assertNotEquals(lines.get("lower-short-name"), lines.get("same-namespace"));
    }

    @Test
    void anAbsentOrMalformedSectionDegradesToEmptyRatherThanThrowing() {
        // A missing line must never stop a rule from compiling.
        assertTrue(DslYamlLineIndex.keyLines(YAML, "no-such-section").isEmpty());
        assertTrue(DslYamlLineIndex.keyLines(null, "auto-matching-rules").isEmpty());
        assertTrue(DslYamlLineIndex.keyLines("key: [unclosed", "auto-matching-rules").isEmpty());
        // A section that is a sequence, not a mapping, has no keys to report.
        assertTrue(DslYamlLineIndex.keyLines("auto-matching-rules:\n  - a\n",
            "auto-matching-rules").isEmpty());
    }
}
