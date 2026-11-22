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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LAL guide and the compiler agree: every method LAL's list names is in the guide's "Supported operators and
 * functions", and every rule the guide shows compiles, so the two cannot drift.
 */
class LALCallablesGuideTest {

    @Test
    void theGuideListsEveryMethodOfTheList() throws Exception {
        final String guide = new String(Files.readAllBytes(Paths.get("../../../docs/en/concepts-and-designs/lal.md")));
        final int start = guide.indexOf("### Supported operators and functions");
        final String section = guide.substring(start, guide.indexOf("\n### ", start + 1));
        // every row, the ones added later included
        LALCallables.get().declaredMethods().forEach((type, methods) -> methods.forEach(method -> assertTrue(
            section.contains("`" + method + "(") || section.contains("." + method + "("),
            type.getSimpleName() + "." + method + "() is in LAL's list but not in lal.md's supported section")));
        LALCallables.get().declaredStatics().forEach((type, methods) -> methods.forEach(method -> assertTrue(
            section.contains(type.getSimpleName() + "." + method + "("), type.getSimpleName() + "." + method)));
    }

    /**
     * Every rule the LAL guide shows compiles: its {@code filter} examples but sketches, with a json parser where one
     * is elided ({@code // ... parser}), and the rules of its YAML examples whose types are on this classpath.
     */
    @Test
    @SuppressWarnings("unchecked")
    void guideExamplesCompile() throws Exception {
        final Path guide = Paths.get("..", "..", "..", "docs", "en", "concepts-and-designs", "lal.md");
        final List<String> failures = new ArrayList<>();
        int compiled = 0;
        StringBuilder block = null;
        boolean yaml = false;
        int start = 0;
        int lineNo = 0;
        for (final String line : Files.readAllLines(guide)) {
            lineNo++;
            if (!line.trim().startsWith("```")) {
                if (block != null) {
                    block.append(line).append('\n');
                }
                continue;
            }
            if (block == null) {
                block = new StringBuilder();
                yaml = line.trim().equals("```yaml");
                start = lineNo;
                continue;
            }
            final List<String> dsls = new ArrayList<>();
            final String text = block.toString();
            block = null;
            if (yaml && text.contains("dsl:")) {
                final Map<String, Object> config = new Yaml().load(text);
                for (final Map<String, Object> rule : (List<Map<String, Object>>) config.get("rules")) {
                    if (!rule.containsKey("outputType") && !rule.containsKey("inputType")) {
                        dsls.add((String) rule.get("dsl"));
                    }
                }
            } else if (text.trim().startsWith("filter") && !text.matches("(?s).*\\n\\s*\\.\\.\\.\\s*\\n.*")) {
                // A block holding a bare `...` line is a sketch.
                dsls.add(text);
            }
            for (final String dsl : dsls) {
                final boolean typed = dsl.contains("parsed?.response") || dsl.contains("parsed?.commonProperties");
                final String withParser = typed || dsl.matches("(?s).*\\b(json|yaml|text)\\s*\\{.*")
                    ? dsl : dsl.replaceFirst("filter\\s*\\{", "filter { json {}");
                final LALClassGenerator generator = new LALClassGenerator(new ClassPool(true));
                if (typed) {
                    generator.setInputType(HTTPAccessLogEntry.class);
                }
                try {
                    generator.compile(withParser);
                    compiled++;
                } catch (final Exception e) {
                    failures.add("lal.md:" + start + ": " + e.getMessage());
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(compiled > 20, "compiled " + compiled);
    }
}
