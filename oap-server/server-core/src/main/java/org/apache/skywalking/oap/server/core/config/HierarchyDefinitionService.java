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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class HierarchyDefinitionService implements org.apache.skywalking.oap.server.library.module.Service {

    /**
     * Functional interface for building hierarchy matching rules.
     * Implementations are provided by hierarchy-v1 (Groovy) or hierarchy-v2 (pure Java).
     */
    @FunctionalInterface
    public interface HierarchyRuleProvider {
        Map<String, BiFunction<Service, Service, Boolean>> buildRules(Map<String, String> ruleExpressions);
    }

    @Getter
    private final Map<String, Map<String, MatchingRule>> hierarchyDefinition;
    @Getter
    private Map<String, Integer> layerLevels;
    private Map<String, MatchingRule> matchingRules;

    public HierarchyDefinitionService(final CoreModuleConfig moduleConfig,
                                      final HierarchyRuleProvider ruleProvider) {
        this.hierarchyDefinition = new HashMap<>();
        this.layerLevels = new HashMap<>();
        if (moduleConfig.isEnableHierarchy()) {
            this.init(ruleProvider);
            this.checkLayers();
        }
    }

    /**
     * Convenience constructor that uses the default Java rule provider.
     */
    public HierarchyDefinitionService(final CoreModuleConfig moduleConfig) {
        this(moduleConfig, new DefaultJavaRuleProvider());
    }

    /**
     * Default pure Java rule provider with 4 built-in hierarchy matching rules.
     * No Groovy dependency.
     */
    private static class DefaultJavaRuleProvider implements HierarchyRuleProvider {
        @Override
        public Map<String, BiFunction<Service, Service, Boolean>> buildRules(
                final Map<String, String> ruleExpressions) {
            final Map<String, BiFunction<Service, Service, Boolean>> registry = new HashMap<>();
            registry.put("name", (u, l) -> Objects.equals(u.getName(), l.getName()));
            registry.put("short-name", (u, l) -> Objects.equals(u.getShortName(), l.getShortName()));
            registry.put("lower-short-name-remove-ns", (u, l) -> {
                final String sn = l.getShortName();
                final int dot = sn.lastIndexOf('.');
                return dot > 0 && Objects.equals(u.getShortName(), sn.substring(0, dot));
            });
            registry.put("lower-short-name-with-fqdn", (u, l) -> {
                final String sn = u.getShortName();
                final int colon = sn.lastIndexOf(':');
                return colon > 0 && Objects.equals(
                    sn.substring(0, colon),
                    l.getShortName() + ".svc.cluster.local");
            });

            final Map<String, BiFunction<Service, Service, Boolean>> rules = new HashMap<>();
            ruleExpressions.forEach((name, expression) -> {
                final BiFunction<Service, Service, Boolean> fn = registry.get(name);
                if (fn == null) {
                    throw new IllegalArgumentException(
                        "Unknown hierarchy matching rule: " + name
                            + ". Known rules: " + registry.keySet());
                }
                rules.put(name, fn);
            });
            return rules;
        }
    }

    @SuppressWarnings("unchecked")
    private void init(final HierarchyRuleProvider ruleProvider) {
        try {
            final Reader applicationReader = ResourceUtils.read("hierarchy-definition.yml");
            final Yaml yaml = new Yaml();
            final Map<String, Map> config = yaml.loadAs(applicationReader, Map.class);
            final Map<String, Map<String, String>> hierarchy = (Map<String, Map<String, String>>) config.get("hierarchy");
            final Map<String, String> ruleExpressions = (Map<String, String>) config.get("auto-matching-rules");
            this.layerLevels = (Map<String, Integer>) config.get("layer-levels");

            final Map<String, BiFunction<Service, Service, Boolean>> builtRules = ruleProvider.buildRules(ruleExpressions);

            this.matchingRules = ruleExpressions.entrySet().stream().map(entry -> {
                final BiFunction<Service, Service, Boolean> matcher = builtRules.get(entry.getKey());
                if (matcher == null) {
                    throw new IllegalStateException(
                        "HierarchyRuleProvider did not produce a matcher for rule: " + entry.getKey());
                }
                final MatchingRule matchingRule = new MatchingRule(entry.getKey(), entry.getValue(), matcher);
                return Map.entry(entry.getKey(), matchingRule);
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            hierarchy.forEach((layer, lowerLayers) -> {
                final Map<String, MatchingRule> rules = new HashMap<>();
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
        this.layerLevels.keySet().forEach(layer -> {
            if (Layer.nameOf(layer).equals(Layer.UNDEFINED)) {
                throw new IllegalArgumentException(
                    "hierarchy-definition.yml " + layer + " is not a valid layer name.");
            }
        });
        this.hierarchyDefinition.forEach((layer, lowerLayers) -> {
            final Integer layerLevel = this.layerLevels.get(layer);
            if (this.layerLevels.get(layer) == null) {
                throw new IllegalArgumentException(
                    "hierarchy-definition.yml  layer-levels: " + layer + " is not defined");
            }

            for (final String lowerLayer : lowerLayers.keySet()) {
                final Integer lowerLayerLevel = this.layerLevels.get(lowerLayer);
                if (lowerLayerLevel == null) {
                    throw new IllegalArgumentException(
                        "hierarchy-definition.yml  layer-levels: " + lowerLayer + " is not defined.");
                }
                if (layerLevel <= lowerLayerLevel) {
                    throw new IllegalArgumentException(
                        "hierarchy-definition.yml hierarchy: " + layer + " layer-level should be greater than " + lowerLayer + " layer-level.");
                }
            }
        });
    }

    @Getter
    public static class MatchingRule {
        private final String name;
        private final String expression;
        private final BiFunction<Service, Service, Boolean> matcher;

        public MatchingRule(final String name, final String expression,
                            final BiFunction<Service, Service, Boolean> matcher) {
            this.name = name;
            this.expression = expression;
            this.matcher = matcher;
        }

        public boolean match(final Service upper, final Service lower) {
            return matcher.apply(upper, lower);
        }
    }
}
