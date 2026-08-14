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
import org.apache.skywalking.oap.server.core.rule.ext.RuleSetMerger;
import org.apache.skywalking.oap.server.core.dsl.DslYamlLineIndex;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Files.getNameWithoutExtension;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;
import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isEmpty;

@Data
@Slf4j
public class LALConfigs {
    private List<LALConfig> rules;
    /**
     * Optional inline layer registrations. When present, each entry is registered through
     * {@code Layer.register(...)} before the rules in this file are compiled, so a
     * LAL file is self-describing for any custom layers it references.
     */
    private List<LayerDefinition> layerDefinitions;

    /**
     * Catalog every LAL rule file lives under, both on disk and as a runtime rule.
     *
     * <p>It stays in {@code sourcePath} because that is the path an operator opens, and it is the
     * segment {@code DslClassNaming.stem} drops, because the generated class's package already
     * identifies the DSL and LAL has no second catalog to disambiguate against. Read from both the
     * stamp site and the strip site on purpose: two copies in two packages is how they drift.
     */
    public static final String LAL_CATALOG = "lal/";

    /**
     * Stamps a rule's identity and attribution coordinates from its file name.
     *
     * <p>Both routes that load LAL — this boot loader and the runtime-rule applier — call this,
     * because the two coordinates are related but not equal and the relationship is easy to get
     * wrong in one place only. {@code sourcePath} is the catalog-qualified path a generated class
     * names; {@code sourceName} is the rule file's identity.
     *
     * <p>The dsl-debugging key is NOT this field's problem: the runtime-rule route never reads it
     * — {@code LalRuleEngine.publishDebugBindings} builds its own key from the rule's bare name —
     * so boot and hot update once disagreed on the extension. That is fixed where it belongs, in
     * {@code RuleKey}'s constructor, which canonicalises the file name so both spellings are one
     * key.
     *
     * @param config   the rule to stamp
     * @param fileName the rule file's name, with or without a YAML extension
     */
    public static void stampSource(final LALConfig config, final String fileName) {
        final String canonical = fileName.endsWith(".yaml") || fileName.endsWith(".yml")
            ? fileName : fileName + ".yaml";
        config.setSourceName(canonical);
        config.setSourcePath(LAL_CATALOG + canonical);
    }

    public static List<LALConfigs> load(final String path, final List<String> files) throws Exception {
        return loadInternal(path, files, null, /* useInstalledManager= */ true);
    }

    /**
     * Load LAL config rules merging the disk allow-list with every
     * {@link org.apache.skywalking.oap.server.core.rule.ext.RuntimeRuleOverrideResolver}
     * discovered on the classpath. {@code manager} is threaded through to the resolvers so
     * the runtime-rule DB resolver can find its DAO; pass {@code null} from test paths that
     * have no module context (resolvers needing the manager return empty contributions in
     * that case).
     *
     * <p>Compared with the legacy disk-only path:
     * <ul>
     *   <li>Files on disk + in allow-list with an INACTIVE resolver entry are skipped.</li>
     *   <li>Files on disk + in allow-list with an ACTIVE resolver entry are parsed from
     *       resolver bytes (override).</li>
     *   <li>Files on disk + in allow-list with no resolver opinion are parsed from disk.</li>
     *   <li>Files in resolver contributions but not on disk are NOT loaded here — pure
     *       runtime LAL rules go through {@code RuleSync.runOnce} post-seal via the
     *       dynamic layer channel, which preserves operator-removable ownership.</li>
     * </ul>
     */
    public static List<LALConfigs> load(final String path, final List<String> files,
                                        final ModuleManager manager) throws Exception {
        return loadInternal(path, files, manager, /* useInstalledManager= */ false);
    }

    private static List<LALConfigs> loadInternal(final String path, final List<String> files,
                                                  final ModuleManager manager,
                                                  final boolean useInstalledManager) throws Exception {
        if (isEmpty(files)) {
            return Collections.emptyList();
        }

        checkArgument(isNotBlank(path), "path cannot be blank");

        try {
            final File[] rules = ResourceUtils.getPathFiles(path);

            // Build the disk baseline keyed by rule name (basename without extension); the
            // sourceFileName side-table preserves the on-disk file name so post-merge config
            // entries can carry it on their `sourceName` field for diagnostics.
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

            // No-manager overload picks up the process-wide ModuleManager set by core.
            // Explicit-manager overload bypasses it.
            final Map<String, byte[]> merged = useInstalledManager
                ? RuleSetMerger.merge("lal", diskBytes)
                : RuleSetMerger.merge("lal", diskBytes, manager);

            final List<LALConfigs> out = new ArrayList<>(merged.size());
            for (final Map.Entry<String, byte[]> e : merged.entrySet()) {
                final String ruleName = e.getKey();
                final byte[] bytes = e.getValue();
                try (final Reader r = new InputStreamReader(
                        new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8)) {
                    final LALConfigs configs = new Yaml().<LALConfigs>loadAs(r, LALConfigs.class);
                    if (configs == null || configs.getRules() == null) {
                        continue;
                    }
                    registerInlineLayers(ruleName, configs);
                    // sourceFileName is only present for entries that came from disk; resolver-
                    // only rules synthesise a name so diagnostics still print something.
                    final String src = sourceFileName.getOrDefault(ruleName, ruleName + ".yaml");
                    // Resolve each rule's line in the SAME text, so a generated class can name the
                    // location an operator opens. Without this the compiler receives only a file
                    // name and every class is labelled unknown. snakeyaml's bean binding discards
                    // positional marks, hence the second compose pass.
                    final DslYamlLineIndex lineIndex = DslYamlLineIndex.index(
                        new String(bytes, StandardCharsets.UTF_8), "rules");
                    for (int i = 0; i < configs.getRules().size(); i++) {
                        stampSource(configs.getRules().get(i), src);
                        configs.getRules().get(i).setLineNo(lineIndex.rule(i).getEntryLine());
                    }
                    out.add(configs);
                } catch (final IOException ioe) {
                    log.debug("Failed to parse LAL rule {}", ruleName, ioe);
                }
            }
            return out;
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Failed to load LAL config rules", e);
        }
    }

    /**
     * Funnel any inline {@code layerDefinitions:} entries through {@code Layer.register}.
     * Conflict checks (reserved-range, name uniqueness, ordinal uniqueness, sealed-state) live
     * in {@code Layer.register}; failures here surface with the offending rule name in
     * the stack trace.
     */
    private static void registerInlineLayers(final String ruleName, final LALConfigs configs) {
        final List<LayerDefinition> defs = configs.getLayerDefinitions();
        if (defs == null || defs.isEmpty()) {
            return;
        }
        for (final LayerDefinition def : defs) {
            try {
                def.register();
            } catch (RuntimeException e) {
                throw new UnexpectedException(
                    "LAL rule " + ruleName + " layerDefinitions entry rejected: " + def, e);
            }
        }
    }
}
