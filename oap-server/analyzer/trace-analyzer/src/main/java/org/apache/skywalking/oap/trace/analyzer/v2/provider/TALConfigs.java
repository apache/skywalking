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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.LayerDefinition;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.apache.skywalking.oap.server.core.rule.ext.RuleSetMerger;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Files.getNameWithoutExtension;
import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isEmpty;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

@Data
@Slf4j
public class TALConfigs {
    /**
     * The prefix every metric declared in this file carries, so a rule writes {@code name "service_cpm"} and the
     * metric is stored as {@code otlp_service_cpm}. Deliberately not {@code meter_*}: that prefix marks a metric
     * MAL produced, and one glance at a metric name should say which engine made it.
     */
    private String metricPrefix;

    private List<TALConfig> rules;

    /**
     * Optional inline layer registrations, the same contract LAL and MAL have: each entry is registered through
     * {@code Layer.register} before the rules in this file are compiled, so a TAL file is self-describing for any
     * custom layer it references.
     */
    private List<LayerDefinition> layerDefinitions;

    /**
     * Catalog every TAL rule file lives under, both on disk and as a runtime rule.
     */
    public static final String TAL_CATALOG = "tal/";

    /**
     * Stamps a rule's identity and attribution coordinates from its file name. Both routes that load TAL, this
     * boot loader and the runtime-rule applier, call this, because the two coordinates are related but not equal.
     *
     * @param config   the rule to stamp
     * @param fileName the rule file's name, with or without a YAML extension
     */
    public static void stampSource(final TALConfig config, final String fileName) {
        final String canonical = fileName.endsWith(".yaml") || fileName.endsWith(".yml")
            ? fileName : fileName + ".yaml";
        config.setSourceName(canonical);
        config.setSourcePath(TAL_CATALOG + canonical);
    }

    /**
     * @param path  the directory the rule files live in, {@code tal} by default
     * @param files the rule file names without extension
     * @return one entry per loaded file
     * @throws Exception when a file cannot be read or a rule cannot be parsed
     */
    public static List<TALConfigs> load(final String path, final List<String> files) throws Exception {
        return loadInternal(path, files, null, true);
    }

    /**
     * Load TAL rules merging the disk allow-list with every
     * {@link org.apache.skywalking.oap.server.core.rule.ext.RuntimeRuleOverrideResolver} on the classpath, so an
     * operator's runtime rule overrides a shipped file of the same name in place.
     *
     * @param path    the directory the rule files live in
     * @param files   the rule file names without extension
     * @param manager threaded through to the resolvers so the runtime-rule DB resolver finds its DAO; null from a
     *                test path with no module context
     * @return one entry per loaded file
     * @throws Exception when a file cannot be read or a rule cannot be parsed
     */
    public static List<TALConfigs> load(final String path, final List<String> files,
                                        final ModuleManager manager) throws Exception {
        return loadInternal(path, files, manager, false);
    }

    private static List<TALConfigs> loadInternal(final String path, final List<String> files,
                                                 final ModuleManager manager,
                                                 final boolean useInstalledManager) throws Exception {
        if (isEmpty(files)) {
            return Collections.emptyList();
        }

        checkArgument(isNotBlank(path), "path cannot be blank");

        try {
            final File[] rules = ResourceUtils.getPathFiles(path);

            final Map<String, byte[]> diskBytes = new HashMap<>();
            final Map<String, String> sourceFileName = new HashMap<>();
            for (final File f : rules) {
                if (!f.isFile()) {
                    continue;
                }
                //noinspection UnstableApiUsage
                final String ruleName = getNameWithoutExtension(f.getName());
                if (!files.contains(ruleName)) {
                    continue;
                }
                try {
                    diskBytes.put(ruleName, Files.readAllBytes(f.toPath()));
                    sourceFileName.put(ruleName, f.getName());
                } catch (final IOException ioe) {
                    log.debug("Failed to read file {}", f, ioe);
                }
            }

            final Map<String, byte[]> merged = useInstalledManager
                ? RuleSetMerger.merge("tal", diskBytes)
                : RuleSetMerger.merge("tal", diskBytes, manager);

            final List<TALConfigs> out = new ArrayList<>(merged.size());
            for (final Map.Entry<String, byte[]> e : merged.entrySet()) {
                final String ruleName = e.getKey();
                try {
                    final TALConfigs configs = parse(
                        ruleName, e.getValue(), sourceFileName.getOrDefault(ruleName, ruleName + ".yaml"));
                    if (configs != null) {
                        out.add(configs);
                    }
                } catch (final IOException ioe) {
                    log.debug("Failed to parse TAL rule {}", ruleName, ioe);
                }
            }
            return out;
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Failed to load TAL config rules", e);
        }
    }

    /**
     * Parse one rule file's bytes into its rules, with every rule stamped with the coordinates a generated class
     * reports: its source path, its line in this text, and the file's metric prefix.
     *
     * <p>Shared by the boot loader above and by any caller holding the bytes already, so a rule's line number is
     * computed in exactly one place. It has to be computed from the text rather than taken from snakeyaml,
     * whose bean binding discards positional marks, which is why this reads the same bytes twice.
     *
     * @param ruleName       the file's name without extension, used in diagnostics
     * @param bytes          the file's content
     * @param sourceFileName the file's name as an operator sees it, with extension
     * @return the parsed rules, or null when the file declares none
     * @throws IOException when the bytes cannot be read
     */
    public static TALConfigs parse(final String ruleName, final byte[] bytes, final String sourceFileName)
        throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            final TALConfigs configs = new Yaml().loadAs(reader, TALConfigs.class);
            if (configs == null || configs.getRules() == null) {
                return null;
            }
            registerInlineLayers(ruleName, configs);
            final DslYamlLineIndex lineIndex =
                DslYamlLineIndex.index(new String(bytes, StandardCharsets.UTF_8), "rules");
            for (int i = 0; i < configs.getRules().size(); i++) {
                final TALConfig config = configs.getRules().get(i);
                stampSource(config, sourceFileName);
                config.setLineNo(lineIndex.rule(i).getEntryLine());
                config.setMetricPrefix(configs.getMetricPrefix());
            }
            return configs;
        }
    }

    /**
     * Funnel any inline {@code layerDefinitions:} entries through {@code Layer.register}. The conflict checks live
     * in {@code Layer.register}; a failure here surfaces with the offending rule name.
     */
    private static void registerInlineLayers(final String ruleName, final TALConfigs configs) {
        final List<LayerDefinition> defs = configs.getLayerDefinitions();
        if (defs == null || defs.isEmpty()) {
            return;
        }
        for (final LayerDefinition def : defs) {
            try {
                def.register();
            } catch (RuntimeException e) {
                throw new UnexpectedException(
                    "TAL rule " + ruleName + " layerDefinitions entry rejected: " + def, e);
            }
        }
    }
}
