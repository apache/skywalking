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

package org.apache.skywalking.oap.server.core.config;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class HierarchyDefinitionService implements org.apache.skywalking.oap.server.library.module.Service {

    @Getter
    private final Map<String, Map<String, MatchingRule>> hierarchyDefinition;
    private Map<String, MatchingRule> matchingRules;

    public HierarchyDefinitionService(CoreModuleConfig moduleConfig) {
        this.hierarchyDefinition = new HashMap<>();
        if (moduleConfig.isEnableHierarchy()) {
            this.init();
            this.checkLayers();
        }
    }

    @SuppressWarnings("unchecked")
    private void init() {
        try {
            Reader applicationReader = ResourceUtils.read("hierarchy-definition.yml");
            Yaml yaml = new Yaml();
            Map<String, Map> config = yaml.loadAs(applicationReader, Map.class);
            Map<String, Map<String, String>> hierarchy = (Map<String, Map<String, String>>) config.get("hierarchy");
            Map<String, String> matchingRules = (Map<String, String>) config.get("auto-matching-rules");
            this.matchingRules = matchingRules.entrySet().stream().map(entry -> {
                MatchingRule matchingRule = new MatchingRule(entry.getKey(), entry.getValue());
                return Map.entry(entry.getKey(), matchingRule);
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            hierarchy.forEach((layer, lowerLayers) -> {
                Map<String, MatchingRule> rules = new HashMap<>();
                lowerLayers.forEach((lowerLayer, ruleName) -> {
                    rules.put(lowerLayer, this.matchingRules.get(ruleName));
                });
                this.hierarchyDefinition.put(layer, rules);
            });
        } catch (FileNotFoundException e) {
            throw new UnexpectedException("hierarchy-definition.yml not found.", e);
        }
    }

    private void checkLayers() {
        this.hierarchyDefinition.forEach((layer, lowerLayers) -> {
            if (lowerLayers.containsKey(layer)) {
                throw new IllegalArgumentException(
                    "hierarchy-definition.yml " + layer + " contains recursive hierarchy relation.");
            }
            checkRecursive(layer);
        });
    }

    private void checkRecursive(String layerName) {
        try {
            Map<String, MatchingRule> lowerLayers = this.hierarchyDefinition.get(layerName);
            if (lowerLayers == null) {
                return;
            }
            lowerLayers.keySet().forEach(this::checkRecursive);
        } catch (Throwable e) {
            throw new IllegalArgumentException(
                "hierarchy-definition.yml " + layerName + " contains recursive hierarchy relation.");
        }
    }

    @Getter
    public static class MatchingRule {
        private final String name;
        private final String expression;
        private final Closure<Boolean> closure;

        @SuppressWarnings("unchecked")
        public MatchingRule(final String name, final String expression) {
            this.name = name;
            this.expression = expression;
            GroovyShell sh = new GroovyShell();
            closure = (Closure<Boolean>) sh.evaluate(expression);
        }
    }
}
