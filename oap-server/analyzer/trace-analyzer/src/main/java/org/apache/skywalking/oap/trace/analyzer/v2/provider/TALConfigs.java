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
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
     * Modules the file's rules need, such as {@code gen-ai-analyzer}: the file is compiled and run only when every
     * one is loaded, and the trace analyzer declares them among its required modules so they start first.
     */
    private List<String> requiredModules;

    /**
     * @param moduleManager the module manager, or null in unit tests, where every module counts as loaded
     * @return whether every module the file requires is loaded
     */
    public boolean requiredModulesLoaded(final ModuleManager moduleManager) {
        if (requiredModules == null || moduleManager == null) {
            return true;
        }
        for (final String module : requiredModules) {
            if (!moduleManager.has(module)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Catalog every TAL rule file lives under.
     */
    public static final String TAL_CATALOG = "tal/";

    /**
     * Stamps a rule's identity and attribution coordinates from its file name; the two are related but not equal.
     *
     * @param config   the rule to stamp
     * @param fileName the rule file's name, with or without a YAML extension
     */
    public static void stampSource(final TALConfig config, final String fileName) {
        stampVerbatim(config, isYamlName(fileName) ? fileName : fileName + ".yaml");
    }

    /**
     * @return whether the file name ends with {@code .yaml} or {@code .yml}, in any case
     */
    private static boolean isYamlName(final String fileName) {
        final String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    /**
     * A file read from disk is named as it is, {@code pay.conf} and an extensionless {@code payments} included.
     */
    private static void stampVerbatim(final TALConfig config, final String fileName) {
        config.setSourceName(fileName);
        config.setSourcePath(TAL_CATALOG + fileName);
    }

    /**
     * @param path  the directory the rule files live in, {@code tal} by default
     * @param files the rule file names without extension
     * @return one entry per loaded file
     * @throws Exception when a file cannot be read or a rule cannot be parsed
     */
    public static List<TALConfigs> load(final String path, final List<String> files) throws Exception {
        return loadInternal(path, files, null, true, false);
    }

    /**
     * Load the TAL rule files through {@link RuleSetMerger}, as LAL and MAL load theirs. {@code Catalog} has no TAL
     * entry, so no runtime rule can be created for TAL and the merge returns the files on disk as they are.
     *
     * @param path    the directory the rule files live in
     * @param files   the rule file names without extension
     * @param manager threaded through to the merger's resolvers; null from a test path with no module context
     * @return one entry per loaded file
     * @throws Exception when a file cannot be read or a rule cannot be parsed
     */
    public static List<TALConfigs> load(final String path, final List<String> files,
                                        final ModuleManager manager) throws Exception {
        return loadInternal(path, files, manager, false, false);
    }

    /**
     * As {@link #load(String, List, ModuleManager)}, for a list whose order is part of its contract, as the native
     * rules' is: the files come back in the order listed, and a listed file that does not exist fails.
     *
     * @param path    the directory the rule files live in
     * @param files   the rule file names without extension, in the order their rules run
     * @param manager threaded through to the merger's resolvers; null from a test path with no module context
     * @return one entry per loaded file, in the order listed; a file its {@code requiredModules} skip has none
     * @throws Exception when a file does not exist, cannot be read or a rule cannot be parsed
     */
    public static List<TALConfigs> loadInOrder(final String path, final List<String> files,
                                               final ModuleManager manager) throws Exception {
        return loadInternal(path, files, manager, false, true);
    }

    private static List<TALConfigs> loadInternal(final String path, final List<String> files,
                                                 final ModuleManager manager,
                                                 final boolean useInstalledManager,
                                                 final boolean inOrder) throws Exception {
        if (isEmpty(files)) {
            return Collections.emptyList();
        }

        checkArgument(isNotBlank(path), "path cannot be blank");

        try {
            final File[] rules = ResourceUtils.getPathFiles(path);

            final Map<String, byte[]> diskBytes = new HashMap<>();
            final Map<String, String> sourceFileName = new HashMap<>();
            for (final Map.Entry<String, File> selected : selectRuleFiles(path, rules, files).entrySet()) {
                final File f = selected.getValue();
                sourceFileName.put(selected.getKey(), f.getName());
                try {
                    diskBytes.put(selected.getKey(), Files.readAllBytes(f.toPath()));
                } catch (final IOException ioe) {
                    log.warn("TAL rule file {} cannot be read; its rules are not loaded", f, ioe);
                }
            }

            final Map<String, byte[]> merged = useInstalledManager
                ? RuleSetMerger.merge("tal", diskBytes)
                : RuleSetMerger.merge("tal", diskBytes, manager);
            for (final String name : files) {
                if (!merged.containsKey(name)) {
                    if (inOrder) {
                        throw new ModuleStartException(
                            "TAL rule file " + path + "/" + name + ".yaml is listed but does not exist");
                    }
                    // Most likely a misspelled talFiles entry, which leaves that file's rules off, so it is worth
                    // more than the INFO count the provider logs.
                    log.warn("talFiles names '{}', but no {}/{}.yaml exists", name, path, name);
                }
            }

            final List<TALConfigs> out = new ArrayList<>(merged.size());
            final List<Map.Entry<String, byte[]>> entries = new ArrayList<>(merged.entrySet());
            if (inOrder) {
                entries.sort(Comparator.comparingInt(e -> files.indexOf(e.getKey())));
            }
            for (final Map.Entry<String, byte[]> e : entries) {
                final String ruleName = e.getKey();
                try {
                    final String diskName = sourceFileName.get(ruleName);
                    final TALConfigs configs = parse(ruleName, e.getValue(),
                        diskName != null ? diskName : ruleName + ".yaml", manager, diskName != null);
                    if (configs != null) {
                        out.add(configs);
                    }
                } catch (final IOException ioe) {
                    log.warn("TAL rule file {} cannot be parsed; its rules are not loaded", ruleName, ioe);
                }
            }
            return out;
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Failed to load TAL config rules", e);
        }
    }

    /**
     * The modules the named rule files list under {@code requiredModules}, read before any module starts so the
     * trace analyzer can declare them as required. Only that key is read; a file that is missing or does not parse
     * contributes none here and is reported when the rules load.
     *
     * @param path  the rule directory
     * @param files the rule files, without extension
     * @return the modules, in file order
     */
    public static Set<String> requiredModulesOf(final String path, final List<String> files) {
        final Set<String> modules = new LinkedHashSet<>();
        if (isEmpty(files) || !isNotBlank(path)) {
            return modules;
        }
        try {
            final Map<String, File> selected;
            try {
                selected = selectRuleFiles(path, ResourceUtils.getPathFiles(path), files);
            } catch (final ModuleStartException e) {
                log.debug("TAL rule files not read for their requiredModules: {}", e.getMessage());
                return modules;
            }
            for (final File f : selected.values()) {
                try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(Files.readAllBytes(f.toPath())), StandardCharsets.UTF_8)) {
                    final Object loaded = new Yaml().load(reader);
                    if (loaded instanceof Map && ((Map<?, ?>) loaded).get("requiredModules") instanceof List) {
                        for (final Object module : (List<?>) ((Map<?, ?>) loaded).get("requiredModules")) {
                            modules.add(String.valueOf(module));
                        }
                    }
                } catch (final IOException | RuntimeException e) {
                    log.debug("TAL rule file {} not read for its requiredModules", f, e);
                }
            }
        } catch (final FileNotFoundException e) {
            log.debug("TAL rule directory {} not found while reading requiredModules", path, e);
        }
        return modules;
    }

    /**
     * The file each listed name reads: the one file named {@code <name>.<extension>}, as ever; of several, the one
     * {@code .yaml} or {@code .yml} file, in any case, the others, such as an editor's {@code <name>.yaml~}, left
     * with a warning; and when that leaves no single file, a boot failure naming them all, as one would otherwise
     * replace the other in whatever order the directory lists them.
     *
     * @param path  the rule directory, for messages
     * @param files every file in it
     * @param names the listed names
     * @return each listed name that has a file, to that file
     * @throws ModuleStartException when a name has several candidate files
     */
    private static Map<String, File> selectRuleFiles(final String path, final File[] files, final List<String> names)
        throws ModuleStartException {
        final Map<String, List<File>> byName = new TreeMap<>();
        for (final File f : files) {
            //noinspection UnstableApiUsage
            final String name = getNameWithoutExtension(f.getName());
            if (f.isFile() && names.contains(name)) {
                byName.computeIfAbsent(name, n -> new ArrayList<>()).add(f);
            }
        }
        final Map<String, File> selected = new LinkedHashMap<>();
        for (final Map.Entry<String, List<File>> entry : byName.entrySet()) {
            final List<File> candidates = entry.getValue();
            if (candidates.size() == 1) {
                selected.put(entry.getKey(), candidates.get(0));
                continue;
            }
            final List<File> yaml = new ArrayList<>();
            for (final File f : candidates) {
                if (isYamlName(f.getName())) {
                    yaml.add(f);
                }
            }
            final Set<String> listed = new TreeSet<>();
            candidates.forEach(f -> listed.add(path + "/" + f.getName()));
            if (yaml.size() != 1) {
                throw new ModuleStartException(
                    "TAL rule files " + listed + " all name the rule file " + entry.getKey() + "; keep one");
            }
            log.warn("TAL rule files {} all name the rule file {}; only {} is read", listed, entry.getKey(),
                     yaml.get(0).getName());
            selected.put(entry.getKey(), yaml.get(0));
        }
        return selected;
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
        return parse(ruleName, bytes, sourceFileName, null);
    }

    /**
     * As {@link #parse(String, byte[], String)}, and a file whose {@code requiredModules} are not all loaded is
     * skipped before its {@code layerDefinitions} register, so an optional file leaves the layer registry alone.
     *
     * @param ruleName       the file's name without extension, used in diagnostics
     * @param bytes          the file's content
     * @param sourceFileName the file's name as an operator sees it, with extension
     * @param manager        the module manager, or null when every module counts as loaded
     * @return the parsed rules, or null when the file declares none or is skipped
     * @throws IOException when the bytes cannot be read
     */
    public static TALConfigs parse(final String ruleName, final byte[] bytes, final String sourceFileName,
                                   final ModuleManager manager) throws IOException {
        return parse(ruleName, bytes, sourceFileName, manager, false);
    }

    private static TALConfigs parse(final String ruleName, final byte[] bytes, final String sourceFileName,
                                    final ModuleManager manager, final boolean verbatim) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            final TALConfigs configs = new Yaml().loadAs(reader, TALConfigs.class);
            if (configs == null) {
                return null;
            }
            if (!configs.requiredModulesLoaded(manager)) {
                log.info("TAL rule file {} skipped: it requires modules {} and not all are loaded",
                         ruleName, configs.getRequiredModules());
                return null;
            }
            // A file of layerDefinitions alone still declares them.
            registerInlineLayers(ruleName, configs);
            if (configs.getRules() == null) {
                return null;
            }
            final DslYamlLineIndex lineIndex =
                DslYamlLineIndex.index(new String(bytes, StandardCharsets.UTF_8), "rules");
            for (int i = 0; i < configs.getRules().size(); i++) {
                final TALConfig config = configs.getRules().get(i);
                if (verbatim) {
                    stampVerbatim(config, sourceFileName);
                } else {
                    stampSource(config, sourceFileName);
                }
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
