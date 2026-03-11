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

package org.apache.skywalking.oap.server.testing.dsl.lal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.testing.dsl.DslRuleLoader;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads LAL rules from YAML files in a {@code test-lal/} directory structure.
 * Pairs each rule with its companion input data ({@code .input.data} or {@code .data.yaml}).
 */
public final class LalRuleLoader {

    private LalRuleLoader() {
    }

    /**
     * Loads all LAL rules from the given directory.
     *
     * @param lalDir the {@code test-lal/} directory containing subdirectories
     *               (e.g., {@code oap-cases/}, {@code feature-cases/})
     * @return rules grouped by relative YAML file path
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<LalTestRule>> loadAllRules(
            final Path lalDir) throws Exception {
        final Map<String, List<LalTestRule>> result = new LinkedHashMap<>();
        final Yaml yaml = new Yaml();

        final List<File> yamlFiles = new ArrayList<>();
        DslRuleLoader.collectYamlFiles(lalDir.toFile(), yamlFiles);

        for (final File file : yamlFiles) {
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null || !config.containsKey("rules")) {
                continue;
            }
            final List<Map<String, Object>> rules =
                (List<Map<String, Object>>) config.get("rules");
            if (rules == null) {
                continue;
            }

            // Load companion input data file
            Map<String, Object> inputData =
                DslRuleLoader.loadCompanionFile(file, ".input.data");
            if (inputData == null) {
                inputData = DslRuleLoader.loadCompanionFile(file, ".data.yaml");
            }

            final List<LalTestRule> lalRules = new ArrayList<>();
            final String[] lines = content.split("\n");
            final Map<String, Integer> nameCount = new HashMap<>();

            for (final Map<String, Object> rule : rules) {
                final String name = (String) rule.get("name");
                final String dslStr = (String) rule.get("dsl");
                if (name == null || dslStr == null) {
                    continue;
                }
                final String inputType = (String) rule.get("inputType");
                final String outputType = (String) rule.get("outputType");
                final String layer = (String) rule.get("layer");
                final boolean v2Only = Boolean.TRUE.equals(rule.get("v2Only"));
                final int count = nameCount.merge(name, 1, Integer::sum);
                final int lineNo = DslRuleLoader.findRuleLine(lines, name, count);

                final Object ruleInput = inputData != null
                    ? inputData.get(name) : null;
                final List<Map<String, Object>> inputs;
                if (ruleInput instanceof List) {
                    inputs = (List<Map<String, Object>>) ruleInput;
                } else if (ruleInput instanceof Map) {
                    inputs = Collections.singletonList(
                        (Map<String, Object>) ruleInput);
                } else {
                    inputs = Collections.emptyList();
                }

                lalRules.add(new LalTestRule(
                    name, dslStr, inputType, outputType, layer,
                    v2Only, inputs, file, lineNo));
            }

            if (!lalRules.isEmpty()) {
                final String relative = lalDir.relativize(file.toPath()).toString();
                result.put("lal/" + relative, lalRules);
            }
        }
        return result;
    }
}
