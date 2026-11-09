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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * The settings the shipped {@code application.yml} gives when no environment variable is set: a
 * {@code ${NAME:default}} value resolves to its default, as the OAP's configuration loader resolves it.
 */
final class CheckerApplicationYml {
    private static final String APPLICATION_YML = "../../../oap-server/server-starter/src/main/resources/application.yml";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:(.*)}");
    private static final Map<String, Object> MODULES = load();

    private CheckerApplicationYml() {
    }

    /**
     * @return the setting of the provider the module's selector picks, resolved
     * @throws IllegalStateException when the shipped file does not set it
     */
    static String setting(final String module, final String key) {
        return setting(module, selector(module), key);
    }

    /**
     * @return the setting of the named provider, resolved, for a module whose selector picks none by default
     * @throws IllegalStateException when the shipped file does not set it
     */
    static String setting(final String module, final String provider, final String key) {
        final Object value = section(module, provider).get(key);
        if (value == null) {
            throw new IllegalStateException(module + "." + provider + "." + key + " is not set in " + APPLICATION_YML);
        }
        return resolve(value);
    }

    /**
     * @return whether the module's selector picks a provider, not {@code -}
     */
    static boolean loaded(final String module) {
        return !"-".equals(selector(module));
    }

    private static String selector(final String module) {
        final Object selector = section(module).get("selector");
        if (selector == null) {
            throw new IllegalStateException(module + " has no selector in " + APPLICATION_YML);
        }
        return resolve(selector);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(final String... path) {
        Map<String, Object> section = MODULES;
        for (final String name : path) {
            final Object child = section.get(name);
            if (!(child instanceof Map)) {
                throw new IllegalStateException(String.join(".", path) + " is not a section of " + APPLICATION_YML);
            }
            section = (Map<String, Object>) child;
        }
        return section;
    }

    private static String resolve(final Object value) {
        final String text = String.valueOf(value);
        final Matcher matcher = PLACEHOLDER.matcher(text);
        return matcher.matches() ? matcher.group(1) : text;
    }

    private static Map<String, Object> load() {
        try (Reader reader = Files.newBufferedReader(Paths.get(APPLICATION_YML), StandardCharsets.UTF_8)) {
            return new Yaml().load(reader);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
