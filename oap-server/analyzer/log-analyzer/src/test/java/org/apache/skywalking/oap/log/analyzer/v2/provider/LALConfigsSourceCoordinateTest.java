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

package org.apache.skywalking.oap.log.analyzer.v2.provider;

import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coordinate a LAL rule reaches the compiler with, resolved the way the LOADERS resolve it.
 *
 * <p>This is the test that was missing. The earlier attribution tests called
 * {@code generator.setYamlSource("lal/execution-basic.yaml:110")} — constructing by hand exactly
 * the input the formatter wants. They proved the formatter formats. They could not, and did not,
 * notice that production supplied only a bare file name with no line, so every real LAL class was
 * unlabelled while the tests passed.
 *
 * <p>Both loaders must agree: {@code LALConfigs} on the boot path and {@code LalFileApplier} on the
 * runtime-rule hot-update path. The second one stamped {@code sourceName} but never {@code lineNo},
 * so a hot-updated rule compiled to a differently-labelled class than its disk-loaded twin.
 */
class LALConfigsSourceCoordinateTest {

    /** Rules at deliberately distinct lines, so an off-by-one or a shared value cannot pass. */
    private static final String YAML =
        "rules:\n"                                  // 1
            + "  - name: first\n"                   // 2
            + "    layer: GENERAL\n"                // 3
            + "    dsl: |\n"                        // 4
            + "      filter {\n"                    // 5
            + "        text {}\n"                   // 6
            + "        sink {}\n"                   // 7
            + "      }\n"                           // 8
            + "  - name: second\n"                  // 9
            + "    layer: GENERAL\n"                // 10
            + "    dsl: |\n"                        // 11
            + "      filter {\n"                    // 12
            + "        json {}\n"                   // 13
            + "        sink {}\n"                   // 14
            + "      }\n";                          // 15

    /** Exactly the resolution both loaders perform. */
    private static List<LALConfig> loadAndStamp() {
        final LALConfigs configs = new Yaml().loadAs(YAML, LALConfigs.class);
        final DslYamlLineIndex index = DslYamlLineIndex.index(YAML, "rules");
        for (int i = 0; i < configs.getRules().size(); i++) {
            configs.getRules().get(i).setSourceName("execution-basic.yaml");
            configs.getRules().get(i).setLineNo(index.rule(i).getEntryLine());
        }
        return configs.getRules();
    }

    @Test
    void eachRuleResolvesToItsOwnEntryLine() {
        final List<LALConfig> rules = loadAndStamp();

        assertEquals(2, rules.get(0).getLineNo(), "first rule's `- name:` anchor");
        assertEquals(9, rules.get(1).getLineNo(),
            "the second rule must not be shifted by the first rule's multi-line dsl block");
    }

    @Test
    void theSecondRuleIsNotGivenTheFirstRuleLine() {
        final List<LALConfig> rules = loadAndStamp();

        // A loader that stamped a single file-level line, or none, would collapse these.
        assertNotEquals(rules.get(0).getLineNo(), rules.get(1).getLineNo());
    }

    @Test
    void aRuleWithNoLineWouldProduceAnUnlabelledClass() {
        // What production did before: sourceName only. The coordinate the generator receives is
        // then just a file name, so the class carries no _L segment and SourceFile no line.
        final LALConfigs configs = new Yaml().loadAs(YAML, LALConfigs.class);
        configs.getRules().forEach(c -> c.setSourceName("execution-basic.yaml"));

        assertEquals(0, configs.getRules().get(0).getLineNo(),
            "an unstamped rule has no line — this is the state that shipped");

        // and the coordinate assembled from it carries no line at all
        final LALConfig unstamped = configs.getRules().get(0);
        final String coordinate = unstamped.getLineNo() > 0
            ? unstamped.getSourceName() + ":" + unstamped.getLineNo()
            : unstamped.getSourceName();
        assertTrue(coordinate.indexOf(':') < 0,
            "without a line the generator receives a bare file name: " + coordinate);
    }
}
