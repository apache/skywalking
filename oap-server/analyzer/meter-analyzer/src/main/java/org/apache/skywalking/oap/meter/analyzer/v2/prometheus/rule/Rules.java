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

package org.apache.skywalking.oap.meter.analyzer.v2.prometheus.rule;

import java.io.ByteArrayInputStream;
import java.io.File;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.stream.Stream;

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.rule.ext.RuleSetMerger;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Rules is factory to instance {@link Rule} from a local file.
 */
public class Rules {
    private static final Logger LOG = LoggerFactory.getLogger(Rule.class);

    /**
     * Default-manager entry point. Picks up the process-wide {@link ModuleManager} set by
     * core during start, so receivers don't have to thread it through their own loaders.
     * Tests with no core boot get an empty resolver list and pure disk-only loading.
     */
    public static List<Rule> loadRules(final String path, List<String> enabledRules) throws IOException {
        return loadInternal(path, enabledRules, null, /* useInstalledManager= */ true);
    }

    /**
     * Explicit-manager entry point — primarily for receivers that already hold a
     * {@link ModuleManager} and want to bypass the process-wide one.
     *
     * <p>The {@code path} doubles as the catalog identifier passed to resolvers — rule
     * directories under {@code server-starter/src/main/resources/} (for instance
     * {@code otel-rules}, {@code log-mal-rules}, {@code envoy-metrics-rules}) already align
     * with the runtime-rule catalog namespace.
     */
    public static List<Rule> loadRules(final String path, List<String> enabledRules,
                                       final ModuleManager manager) throws IOException {
        return loadInternal(path, enabledRules, manager, /* useInstalledManager= */ false);
    }

    private static List<Rule> loadInternal(final String path, List<String> enabledRules,
                                            final ModuleManager manager,
                                            final boolean useInstalledManager) throws IOException {

        final Path root = ResourceUtils.getPath(path);

        Map<String, Boolean> formedEnabledRules = enabledRules
                .stream()
                .map(rule -> {
                    rule = rule.trim();
                    if (rule.startsWith("/")) {
                        rule = rule.substring(1);
                    }
                    if (!rule.endsWith(".yaml") && !rule.endsWith(".yml")) {
                        return rule + "{.yaml,.yml}";
                    }
                    return rule;
                })
                .collect(Collectors.toMap(rule -> rule, $ -> false));

        // Disk baseline: every file under `root` that matches the enabled-rules glob, keyed
        // by relative path without extension (the rule name).
        final Map<String, byte[]> diskBytes = new HashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> {
                File f = p.toFile();
                if (!f.isFile() || f.isHidden()) {
                    return false;
                }
                return formedEnabledRules.keySet().stream().anyMatch(rule -> {
                    boolean matches = FileSystems.getDefault().getPathMatcher("glob:" + rule)
                        .matches(root.relativize(p));
                    if (matches) {
                        formedEnabledRules.put(rule, true);
                    }
                    return matches;
                });
            }).forEach(p -> {
                final String rel = root.relativize(p).toString();
                final String ruleName = rel.substring(0, rel.lastIndexOf('.'));
                try {
                    diskBytes.put(ruleName, Files.readAllBytes(p));
                } catch (IOException e) {
                    throw new UnexpectedException("Load rule file " + p.getFileName() + " failed", e);
                }
            });
        }

        if (formedEnabledRules.containsValue(false)) {
            List<String> rulesNotFound = formedEnabledRules.keySet().stream()
                    .filter(rule -> !formedEnabledRules.get(rule))
                    .collect(Collectors.toList());
            throw new UnexpectedException("Some configuration files of enabled rules are not found, enabled rules: " + rulesNotFound);
        }

        // Merge with classpath-discovered resolvers (runtime-rule DB, plus any future
        // priority-ranked source). Resolvers contributing INACTIVE drop their entries;
        // ACTIVE substitutes content. Resolver-only rules (not on disk) are included.
        final Map<String, byte[]> merged = useInstalledManager
            ? RuleSetMerger.merge(path, diskBytes)
            : RuleSetMerger.merge(path, diskBytes, manager);

        return merged.entrySet().stream()
            .map(e -> parseRule(e.getKey(), e.getValue()))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static Rule parseRule(final String ruleName, final byte[] bytes) {
        try (Reader r = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            Rule rule = new Yaml().loadAs(r, Rule.class);
            if (rule == null) {
                return null;
            }
            rule.setName(ruleName);
            return rule;
        } catch (IOException e) {
            throw new UnexpectedException("Load rule " + ruleName + " failed", e);
        }
    }
}
