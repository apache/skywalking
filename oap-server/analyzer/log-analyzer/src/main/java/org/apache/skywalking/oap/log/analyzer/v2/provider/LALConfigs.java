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
import org.apache.skywalking.oap.server.core.rule.ext.RuleSetMerger;
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
     *   <li>Files in {@code files} but missing on disk are still loaded if a resolver
     *       contributes ACTIVE content for them (DB-only LAL rules).</li>
     *   <li>Files on disk + in allow-list with an INACTIVE resolver entry are skipped.</li>
     *   <li>Files on disk + in allow-list with an ACTIVE resolver entry are parsed from
     *       resolver bytes (override).</li>
     *   <li>Files on disk + in allow-list with no resolver opinion are parsed from disk.</li>
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
                    // sourceFileName is only present for entries that came from disk; resolver-
                    // only rules synthesise a name so diagnostics still print something.
                    final String src = sourceFileName.getOrDefault(ruleName, ruleName + ".yaml");
                    configs.getRules().forEach(c -> c.setSourceName(src));
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
}
