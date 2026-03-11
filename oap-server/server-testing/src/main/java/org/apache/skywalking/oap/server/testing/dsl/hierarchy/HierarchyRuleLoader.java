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

package org.apache.skywalking.oap.server.testing.dsl.hierarchy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads hierarchy rule definitions and test pairs from YAML files.
 */
public final class HierarchyRuleLoader {

    private HierarchyRuleLoader() {
    }

    /**
     * Loads hierarchy rule expressions from a YAML file.
     *
     * @param yamlFile path to the hierarchy definition YAML
     * @return map of rule name to expression string
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> loadRuleExpressions(
            final Path yamlFile) throws Exception {
        final String content = Files.readString(yamlFile);
        final Map<String, Object> config = new Yaml().load(content);
        final Map<String, String> result = new LinkedHashMap<>();
        if (config == null) {
            return result;
        }
        final Map<String, String> rules =
            (Map<String, String>) config.get("auto-matching-rules");
        if (rules != null) {
            result.putAll(rules);
        }
        return result;
    }

    /**
     * Loads test pairs from a companion {@code .data.yaml} file.
     *
     * <p>Expected YAML structure:
     * <pre>
     * input:
     *   rule_name:
     *     - description: "Apache vs nginx"
     *       upper:
     *         name: "Apache"
     *         shortName: "apache"
     *       lower:
     *         name: "nginx"
     *         shortName: "nginx"
     *       expected: true
     * </pre>
     *
     * @param dataFile path to the {@code .data.yaml} file
     * @return test pairs grouped by rule name
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<HierarchyTestPair>> loadTestPairs(
            final Path dataFile) throws Exception {
        final String content = Files.readString(dataFile);
        final Map<String, Object> config = new Yaml().load(content);
        final Map<String, List<HierarchyTestPair>> result = new LinkedHashMap<>();
        if (config == null) {
            return result;
        }

        final Map<String, Object> input =
            (Map<String, Object>) config.get("input");
        if (input == null) {
            return result;
        }

        for (final Map.Entry<String, Object> entry : input.entrySet()) {
            final String ruleName = entry.getKey();
            final List<?> pairs = (List<?>) entry.getValue();
            final List<HierarchyTestPair> testPairs = new ArrayList<>();

            for (final Object pairObj : pairs) {
                final Map<String, Object> pair = (Map<String, Object>) pairObj;
                final String description = (String) pair.get("description");
                final Map<String, String> upper =
                    (Map<String, String>) pair.get("upper");
                final Map<String, String> lower =
                    (Map<String, String>) pair.get("lower");
                final Boolean expected = (Boolean) pair.get("expected");

                testPairs.add(new HierarchyTestPair(
                    description,
                    upper.get("name"), upper.get("shortName"),
                    lower.get("name"), lower.get("shortName"),
                    expected));
            }

            result.put(ruleName, testPairs);
        }
        return result;
    }
}
