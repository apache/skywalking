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

package org.apache.skywalking.oap.server.core.rule.ext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide snapshot of the on-disk static rule content seen at boot, recorded by
 * {@link RuleSetMerger} before any {@link RuntimeRuleOverrideResolver} substitutes operator
 * overrides. The runtime-rule REST handler reads from this registry to compute
 * {@code priorContent} for the delta classifier when no DB row yet exists for a
 * {@code (catalog, name)}.
 *
 * <p>Singleton because resolvers populate it during analyzer-module {@code start()}, before
 * the receiver modules hosting the runtime-rule admin surface boot.
 */
public final class StaticRuleRegistry {

    private static final StaticRuleRegistry ACTIVE = new StaticRuleRegistry();

    /**
     * @return the process-wide singleton. Always non-null; calls on a fresh registry return
     *         {@link Optional#empty()} until {@link #record} has been invoked for that
     *         {@code (catalog, name)}.
     */
    public static StaticRuleRegistry active() {
        return ACTIVE;
    }

    /** Map key is {@code catalog + ":" + name}; matches the runtime-rule catalog naming. */
    private final ConcurrentHashMap<String, String> staticContent = new ConcurrentHashMap<>();

    private StaticRuleRegistry() {
    }

    /**
     * Record the raw disk content for one static rule. Idempotent — repeated calls for the
     * same key replace the recorded bytes, which is the desired behaviour when a boot pass
     * re-reads the same file.
     *
     * @param catalog catalog identifier (e.g., {@code "otel-rules"}, {@code "log-mal-rules"},
     *                {@code "lal"}).
     * @param name    rule name (file path under catalog root, without extension; may include
     *                {@code /} for nested layouts).
     * @param content raw disk bytes — decoded as UTF-8 and stored as a String for parity with
     *                how DB rows store rule bodies.
     */
    public void record(final String catalog, final String name, final byte[] content) {
        if (catalog == null || name == null || content == null) {
            return;
        }
        staticContent.put(key(catalog, name), new String(content, StandardCharsets.UTF_8));
    }

    /**
     * @return the raw disk content for the given {@code (catalog, name)}, or
     *         {@link Optional#empty()} if no static file was recorded for it.
     */
    public Optional<String> find(final String catalog, final String name) {
        if (catalog == null || name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(staticContent.get(key(catalog, name)));
    }

    /**
     * Read-only view of every {@code catalog:name} → content pair currently recorded. Used by
     * the runtime-rule reconciler to seed synthetic applied-state entries at boot (so tick
     * idempotency works for rules that live only on disk) and to rehydrate after an operator
     * {@code /delete} removes the runtime tombstone covering a shipped static rule.
     *
     * <p>The map is a live read-through view of the registry's backing store; iteration order
     * is unspecified. Callers must not mutate the returned map.
     */
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(staticContent);
    }

    /**
     * Every {@code (name, content)} pair recorded under {@code catalog}, sorted by name.
     * Used by {@code GET /runtime/rule/bundled} to render the static-rule view that UIs
     * merge with the runtime-overrides view from {@code GET /runtime/rule/list}.
     */
    public List<NamedRule> findByCatalog(final String catalog) {
        if (catalog == null) {
            return Collections.emptyList();
        }
        final String prefix = catalog + ":";
        final List<NamedRule> matches = new ArrayList<>();
        for (final Map.Entry<String, String> e : staticContent.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                final String name = e.getKey().substring(prefix.length());
                matches.add(new NamedRule(name, e.getValue()));
            }
        }
        matches.sort((a, b) -> a.name.compareTo(b.name));
        return matches;
    }

    /**
     * Pair of (rule name, raw YAML content) returned by {@link #findByCatalog(String)}.
     * Public so the REST handler can iterate the result directly without a tuple type.
     */
    public static final class NamedRule {
        private final String name;
        private final String content;

        public NamedRule(final String name, final String content) {
            this.name = name;
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Split the registry's {@code catalog:name} key back into its components. Centralised
     * here so callers don't hardcode the separator.
     */
    public static String[] splitKey(final String key) {
        if (key == null) {
            return null;
        }
        final int colon = key.indexOf(':');
        if (colon <= 0 || colon == key.length() - 1) {
            return null;
        }
        return new String[] {key.substring(0, colon), key.substring(colon + 1)};
    }

    /**
     * Test hook — drops every recorded entry. Intentionally package-private so only tests in
     * the same package can reach it; production code must not clear a populated registry.
     */
    void clear() {
        staticContent.clear();
    }

    private static String key(final String catalog, final String name) {
        return catalog + ":" + name;
    }
}
