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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
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
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Loads hierarchy definitions from {@code hierarchy-definition.yml} and compiles
 * matching rules into executable {@code BiFunction<Service, Service, Boolean>}
 * matchers via a pluggable {@link HierarchyRuleProvider} (discovered through Java SPI).
 *
 * <p>Initialization (at startup, in CoreModuleProvider):
 * <ol>
 *   <li>Reads {@code hierarchy-definition.yml} containing three sections:
 *       {@code hierarchy} (layer-to-lower-layer mapping with rule names),
 *       {@code auto-matching-rules} (rule name to expression string),
 *       and {@code layer-levels} (layer to numeric level).</li>
 *   <li>Discovers a {@link HierarchyRuleProvider} via {@code ServiceLoader}
 *       (e.g., {@code CompiledHierarchyRuleProvider} from the hierarchy module).</li>
 *   <li>Calls {@link HierarchyRuleProvider#buildRules} which compiles each rule
 *       expression (e.g., {@code "{ (u, l) -> u.name == l.name }"}) into a
 *       {@code BiFunction} via ANTLR4 + Javassist.</li>
 *   <li>Wraps each compiled matcher in a {@link MatchingRule} and maps them
 *       to the layer hierarchy structure.</li>
 *   <li>Validates all layers exist in the {@code Layer} enum and that upper
 *       layers have higher level numbers than their lower layers.</li>
 * </ol>
 *
 * <p>The resulting {@link #getHierarchyDefinition()} map is consumed by
 * {@link org.apache.skywalking.oap.server.core.hierarchy.HierarchyService}
 * for runtime service matching.
 */
@Slf4j
public class HierarchyDefinitionService implements org.apache.skywalking.oap.server.library.module.Service {

    /**
     * Functional interface for building hierarchy matching rules.
     * Discovered via Java SPI ({@code ServiceLoader}).
     */
    @FunctionalInterface
    public interface HierarchyRuleProvider {
        /**
         * Compiles every hierarchy rule, given both its expression and its location.
         *
         * <p><b>Breaking change.</b> This replaces the former one-argument
         * {@code buildRules(Map)}. A {@code ServiceLoader} provider compiled against that older
         * signature will fail with {@link AbstractMethodError} and must be recompiled against this
         * one. That is deliberate: a provider which cannot report where a rule came from produces
         * classes labelled {@code _Lunknown_}, and a stack frame from one of them leads nowhere.
         * Implementors that genuinely have no line information should pass through an empty map
         * rather than silently dropping the parameter.
         *
         * <p>The lines travel separately because {@code ruleExpressions} is built by snakeyaml's
         * bean binding, which has already discarded positional marks by the time the provider is
         * called; they are recovered by a second compose pass over the same text
         * ({@link DslYamlLineIndex#keyLines}).
         *
         * @param ruleExpressions rule name to expression
         * @param ruleLines       rule name to its 1-based line in {@code hierarchy-definition.yml};
         *                        empty when unresolvable
         * @return rule name to compiled matcher
         */
        Map<String, BiFunction<Service, Service, Boolean>> buildRules(
            Map<String, String> ruleExpressions, Map<String, Integer> ruleLines);
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
     * Convenience constructor that discovers a {@link HierarchyRuleProvider}
     * via Java SPI ({@code ServiceLoader}). Only loads the provider when
     * hierarchy is enabled.
     */
    public HierarchyDefinitionService(final CoreModuleConfig moduleConfig) {
        this.hierarchyDefinition = new HashMap<>();
        this.layerLevels = new HashMap<>();
        if (moduleConfig.isEnableHierarchy()) {
            this.init(loadProvider());
            this.checkLayers();
        }
    }

    /**
     * Discovers a {@link HierarchyRuleProvider} via Java SPI. The provider is registered in
     * {@code META-INF/services/...HierarchyDefinitionService$HierarchyRuleProvider} by the
     * hierarchy analyzer module ({@code CompiledHierarchyRuleProvider}).
     * Takes the first provider found; fails fast if none is on the classpath.
     */
    private static HierarchyRuleProvider loadProvider() {
        final ServiceLoader<HierarchyRuleProvider> loader =
            ServiceLoader.load(HierarchyRuleProvider.class);
        for (final HierarchyRuleProvider provider : loader) {
            log.info("Using hierarchy rule provider: {}", provider.getClass().getName());
            return provider;
        }
        throw new IllegalStateException(
            "No HierarchyRuleProvider found on classpath. "
                + "Ensure the hierarchy analyzer module is included.");
    }

    @SuppressWarnings("unchecked")
    private void init(final HierarchyRuleProvider ruleProvider) {
        try {
            // Bind from the decoded text, then re-compose the SAME text for positional marks:
            // snakeyaml's bean binding discards them, so the rule lines must be read separately.
            // Same idiom as every other rule loader in the DSL path (Rules, LALConfigs,
            // ZabbixConfigs): read the bytes once, decode as UTF-8 explicitly.
            final String yamlText = new String(
                ResourceUtils.readToStream("hierarchy-definition.yml").readAllBytes(), UTF_8);
            final Yaml yaml = new Yaml();
            final Map<String, Map> config = yaml.loadAs(yamlText, Map.class);
            final Map<String, Map<String, String>> hierarchy = (Map<String, Map<String, String>>) config.get("hierarchy");
            final Map<String, String> ruleExpressions = (Map<String, String>) config.get("auto-matching-rules");
            this.layerLevels = (Map<String, Integer>) config.get("layer-levels");

            final Map<String, BiFunction<Service, Service, Boolean>> builtRules = ruleProvider.buildRules(
                ruleExpressions,
                DslYamlLineIndex.keyLines(yamlText, "auto-matching-rules"));

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
        } catch (IOException e) {
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

        /**
         * Evaluates the compiled matching rule against two services.
         * The {@code matcher} is a Javassist-generated {@code BiFunction} compiled
         * from the expression in {@code hierarchy-definition.yml} at startup.
         * If the expression throws at runtime (e.g., NPE from null shortName),
         * the exception propagates to the caller
         * ({@link org.apache.skywalking.oap.server.core.hierarchy.HierarchyService}).
         */
        public boolean match(final Service upper, final Service lower) {
            if (log.isDebugEnabled()) {
                log.debug("[Hierarchy] rule={}, class={}, upper=[{}, {}], lower=[{}, {}]",
                    name, matcher.getClass().getName(),
                    upper.getName(), upper.getShortName(),
                    lower.getName(), lower.getShortName());
            }
            return matcher.apply(upper, lower);
        }
    }
}
