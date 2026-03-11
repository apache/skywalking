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

package org.apache.skywalking.oap.server.testing.dsl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Generic utilities for loading DSL rule files and companion test data.
 * Shared by LAL, MAL, and Hierarchy test frameworks.
 */
public final class DslRuleLoader {

    private DslRuleLoader() {
    }

    /**
     * Recursively collects YAML files from a directory.
     * Skips companion data files ({@code .data.yaml}, {@code .input.data}).
     */
    public static void collectYamlFiles(final File dir, final List<File> result) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File child : children) {
            if (child.isDirectory()) {
                collectYamlFiles(child, result);
            } else {
                final String name = child.getName();
                if ((name.endsWith(".yaml") || name.endsWith(".yml"))
                        && !name.endsWith(".data.yaml")
                        && !name.endsWith(".input.data")) {
                    result.add(child);
                }
            }
        }
    }

    /**
     * Finds the 1-based line number of the Nth occurrence of
     * {@code name: <value>} in YAML content lines.
     */
    public static int findRuleLine(final String[] lines, final String name,
                                   final int occurrence) {
        int found = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2);
            }
            if (trimmed.equals("name: " + name)
                    || trimmed.equals("name: '" + name + "'")
                    || trimmed.equals("name: \"" + name + "\"")) {
                found++;
                if (found == occurrence) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /**
     * Resolves a scripts directory by trying multiple candidate paths.
     *
     * @param candidates relative path candidates to try
     * @return the first existing directory, or {@code null}
     */
    public static Path findScriptsDir(final String... candidates) {
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Loads a companion YAML file alongside a rule YAML file.
     *
     * @param yamlFile the rule YAML file
     * @param suffix   companion suffix (e.g., {@code ".data.yaml"} or {@code ".input.data"})
     * @return parsed YAML as a Map, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadCompanionFile(final File yamlFile,
                                                        final String suffix) throws Exception {
        final String baseName = yamlFile.getName()
            .replaceFirst("\\.(yaml|yml)$", "");
        final File companion = new File(yamlFile.getParent(), baseName + suffix);
        if (!companion.exists()) {
            return null;
        }
        return new Yaml().load(Files.readString(companion.toPath()));
    }

    /**
     * Collects all YAML files from immediate subdirectories of the given dir.
     */
    public static List<File> collectFromSubdirs(final Path dir) {
        final List<File> result = new ArrayList<>();
        final File[] subdirs = dir.toFile().listFiles(File::isDirectory);
        if (subdirs != null) {
            for (final File subdir : subdirs) {
                collectYamlFiles(subdir, result);
            }
        }
        return result;
    }
}
