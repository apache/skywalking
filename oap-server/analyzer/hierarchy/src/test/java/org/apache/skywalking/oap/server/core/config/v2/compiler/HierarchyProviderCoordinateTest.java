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

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link CompiledHierarchyRuleProvider} through the SPI the way
 * {@code HierarchyDefinitionService} does, including the line map it resolves with
 * {@link DslYamlLineIndex#keyLines}.
 *
 * <p>{@code HierarchyRuleExecutionTest} scans the YAML itself and injects a coordinate straight
 * into the generator, so {@code keyLines} returning nothing — or the service failing to pass it —
 * would leave it green. This exercises the path production actually takes.
 */
class HierarchyProviderCoordinateTest {

    private static final String YAML =
        "auto-matching-rules:\n"                            // 1
            + "  lower-short-name: |\n"                     // 2
            + "    { (u, l) -> u.name == l.name }\n"          // 3
            + "  same-name: |\n"                            // 4
            + "    { (u, l) -> u.name == l.name }\n";         // 5

    /** Exactly what HierarchyDefinitionService.init assembles before calling the SPI. */
    private static Map<String, String> expressions() {
        final Map<String, String> out = new LinkedHashMap<>();
        out.put("lower-short-name", "{ (u, l) -> u.name == l.name }");
        out.put("same-name", "{ (u, l) -> u.name == l.name }");
        return out;
    }

    /**
     * Asserts the generated class is named for its rule and line, tolerating the allocation
     * suffix.
     *
     * <p>Hierarchy deduplicates against a process-wide set, so the same rule compiled a second
     * time in the same JVM is named {@code ..._2}. Pinning the whole name would make this test
     * depend on how many other tests in the fork had already compiled it — an ordering coupling
     * that passes or fails on JUnit's method hash.
     *
     * @param compiled the compiled matcher whose class name is under test
     * @param stem     the expected {@code file_L<line>_rule} stem
     */
    private static void assertNamedForItsRule(final Object compiled, final String stem) {
        final String actual = compiled.getClass().getSimpleName();
        assertTrue(actual.matches(Pattern.quote(stem) + "(_\\d+)?"),
            "expected a class named " + stem + " (optionally _N), got " + actual);
    }

    @Test
    void theProviderCompilesEveryRuleAndNamesEachForItsOwnLine() {
        final HierarchyDefinitionService.HierarchyRuleProvider provider =
            new CompiledHierarchyRuleProvider();

        // Compiled once, asserted once: compiling the same rules in two test methods would make
        // the second one's names collide with the first's.
        final Map<String, BiFunction<Service, Service, Boolean>> rules =
            provider.buildRules(expressions(),
                DslYamlLineIndex.keyLines(YAML, "auto-matching-rules"));

        assertEquals(2, rules.size());
        rules.forEach((name, matcher) -> assertNotNull(matcher, "no matcher for " + name));

        // The line reaching the loader is only half of it — it still has to reach the class name,
        // which is what a reader sees in a stack frame before any file is opened.
        assertNamedForItsRule(rules.get("lower-short-name"),
            "hierarchy_definition_L2_lower_short_name");
        assertNamedForItsRule(rules.get("same-name"), "hierarchy_definition_L4_same_name");
    }

    @Test
    void aFrameFromAGeneratedRuleNamesTheRuleFileAndLine() {
        final Map<String, String> expression = new LinkedHashMap<>();
        // Dereferences shortName, so a null one throws from inside the generated apply().
        expression.put("npe-probe", "{ (u, l) -> u.name == l.shortName.substring(0, 1) }");

        final Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("npe-probe", 7);

        final BiFunction<Service, Service, Boolean> rule =
            new CompiledHierarchyRuleProvider().buildRules(expression, lines).get("npe-probe");
        assertNamedForItsRule(rule, "hierarchy_definition_L7_npe_probe");
        final String generatedName = rule.getClass().getName();

        final Service upper = new Service();
        upper.setName("a");
        final Service lower = new Service();

        StackTraceElement generatedFrame = null;
        try {
            rule.apply(upper, lower);
        } catch (final NullPointerException expected) {
            for (final StackTraceElement frame : expected.getStackTrace()) {
                if (frame.getClassName().equals(generatedName)) {
                    generatedFrame = frame;
                    break;
                }
            }
        }

        assertNotNull(generatedFrame, "the generated rule did not appear in the stack trace");
        // What a JVM reports for the frame IS the SourceFile attribute, and it is the whole point
        // of the attribute: no .java is written for hierarchy in any mode, so without the rule
        // coordinate in front, this frame would name a file that exists nowhere. The class's own
        // name is read back rather than restated, so an allocation suffix cannot break this.
        assertEquals("(hierarchy-definition.yml:7)" + rule.getClass().getSimpleName() + ".java",
            generatedFrame.getFileName());
    }

    @Test
    void theResolvedLinesAreTheOnesTheServiceWouldPass() {
        final Map<String, Integer> lines =
            DslYamlLineIndex.keyLines(YAML, "auto-matching-rules");

        // If this map were empty, every hierarchy class would be labelled _Lunknown_ — the state
        // that shipped, and which the generator-injecting test could not detect.
        assertTrue(lines.get("lower-short-name") > 0);
        assertEquals(2, lines.get("lower-short-name"));
        assertEquals(4, lines.get("same-name"));
    }
}
