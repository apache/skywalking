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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Folds a disk-loaded baseline + every {@link RuntimeRuleOverrideResolver} discovered on the
 * classpath into a single {@code (name -> bytes)} map per catalog. The MAL and LAL static-file
 * loaders feed the result directly into their compile pipelines.
 *
 * <h2>Merge order</h2>
 * Resolvers are applied in <strong>ascending</strong> {@link RuntimeRuleOverrideResolver#priority()}
 * order so higher-priority entries overwrite lower-priority ones. The disk map is the initial
 * baseline (priority −∞).
 *
 * <h2>Side effect: {@link StaticRuleRegistry}</h2>
 * The merger calls {@link StaticRuleRegistry#record(String, String, byte[])} for every disk
 * baseline entry before merging, so the runtime apply pipeline's delta classifier can compare
 * a later REST {@code /addOrUpdate} body against the original on-disk content even when a
 * resolver has substituted the boot-time bytes. Recording happens whether or not any resolver
 * is present.
 */
@Slf4j
public final class RuleSetMerger {

    private RuleSetMerger() {
    }

    /**
     * Process-wide {@link ModuleManager} stashed by {@code CoreModuleProvider} during start.
     * Callers (MAL / LAL static loaders) reach it via {@link #merge(String, Map)} so they
     * don't have to thread {@code ModuleManager} through every signature. Tests that don't
     * boot core leave this {@code null} and resolvers needing the manager return empty
     * contributions.
     */
    private static volatile ModuleManager INSTALLED_MANAGER;

    /**
     * Set the process-wide module manager. Called once from
     * {@code CoreModuleProvider.start()} after the management streams are registered.
     * Tests may call with {@code null} to reset between cases.
     */
    public static void installManager(final ModuleManager manager) {
        INSTALLED_MANAGER = manager;
    }

    /**
     * Default-manager overload — the path most production callers take. Looks up the
     * process-wide {@link ModuleManager} installed by core, discovers every
     * {@link RuntimeRuleOverrideResolver} via {@link ServiceLoader}, and merges with the
     * supplied disk baseline.
     */
    public static Map<String, byte[]> merge(final String catalog, final Map<String, byte[]> diskBytes) {
        return merge(catalog, diskBytes, discoverResolvers(), INSTALLED_MANAGER);
    }

    /**
     * Explicit-manager overload for callers that already hold a {@link ModuleManager} (e.g.
     * receivers being updated to thread it through directly). Same merge semantics as the
     * default overload; bypasses the static manager.
     *
     * @param catalog   catalog identifier (e.g. {@code "otel-rules"}, {@code "lal"}). Recorded
     *                  on each {@link StaticRuleRegistry} entry for the runtime delta classifier.
     * @param diskBytes raw disk content keyed by rule name (file basename without extension).
     *                  Already filtered by the loader's allow-list (e.g. enabled rules).
     * @param manager   OAP {@link ModuleManager}, threaded through to each resolver's
     *                  {@link RuntimeRuleOverrideResolver#loadAll(String, ModuleManager)}. May be
     *                  {@code null} when the caller has no module context (tests) — resolvers
     *                  that need it return an empty map gracefully.
     * @return ordered merge of disk + resolvers; entries the merge resolved as
     *         {@link RuntimeRuleOverrideResolver.Decision#INACTIVE} are absent.
     */
    public static Map<String, byte[]> merge(final String catalog,
                                            final Map<String, byte[]> diskBytes,
                                            final ModuleManager manager) {
        return merge(catalog, diskBytes, discoverResolvers(), manager);
    }

    /**
     * Variant with an explicit resolver list — primarily for tests that want to bypass
     * {@link ServiceLoader}.
     */
    public static Map<String, byte[]> merge(final String catalog,
                                            final Map<String, byte[]> diskBytes,
                                            final List<RuntimeRuleOverrideResolver> resolvers,
                                            final ModuleManager manager) {
        // Snapshot the on-disk baseline into StaticRuleRegistry before we start mutating the
        // working map; the runtime-rule delta classifier reads original bytes from there even
        // when a high-priority resolver has substituted them in `out`.
        final StaticRuleRegistry registry = StaticRuleRegistry.active();
        if (registry != null) {
            diskBytes.forEach((name, bytes) -> registry.record(catalog, name, bytes));
        }

        final Map<String, byte[]> out = new HashMap<>(diskBytes);

        if (resolvers == null || resolvers.isEmpty()) {
            return out;
        }

        final List<RuntimeRuleOverrideResolver> ordered = new ArrayList<>(resolvers);
        ordered.sort(Comparator.comparingInt(RuntimeRuleOverrideResolver::priority));

        for (final RuntimeRuleOverrideResolver resolver : ordered) {
            final Map<String, RuntimeRuleOverrideResolver.Resolution> contributions;
            try {
                contributions = resolver.loadAll(catalog, manager);
            } catch (final Throwable t) {
                log.warn("RuntimeRuleOverrideResolver {} loadAll({}) threw — skipping resolver",
                    resolver.getClass().getName(), catalog, t);
                continue;
            }
            if (contributions == null || contributions.isEmpty()) {
                continue;
            }
            contributions.forEach((name, res) -> {
                if (res == null || res.getDecision() == null) {
                    return;
                }
                switch (res.getDecision()) {
                    case ACTIVE:
                        if (res.getContent() == null) {
                            log.warn("Resolver {} returned ACTIVE with null content for {}/{} — ignored",
                                resolver.getClass().getName(), catalog, name);
                            return;
                        }
                        out.put(name, res.getContent());
                        break;
                    case INACTIVE:
                        out.remove(name);
                        break;
                    default:
                        // unreachable — enum is closed
                }
            });
        }
        return out;
    }

    /**
     * Cache the discovered resolver list per process. {@code ServiceLoader} is cheap to
     * iterate but instantiating fresh resolvers per call would defeat their internal
     * caches. Lazy-initialised so tests that swap in stubs via {@link #merge(String, Map, List)}
     * never trigger the discovery path.
     */
    private static volatile List<RuntimeRuleOverrideResolver> CACHED_RESOLVERS;

    private static List<RuntimeRuleOverrideResolver> discoverResolvers() {
        List<RuntimeRuleOverrideResolver> cached = CACHED_RESOLVERS;
        if (cached != null) {
            return cached;
        }
        synchronized (RuleSetMerger.class) {
            cached = CACHED_RESOLVERS;
            if (cached != null) {
                return cached;
            }
            final List<RuntimeRuleOverrideResolver> discovered = new ArrayList<>();
            for (final RuntimeRuleOverrideResolver r : ServiceLoader.load(RuntimeRuleOverrideResolver.class)) {
                discovered.add(r);
                log.info("RuntimeRuleOverrideResolver registered: {} (priority={})",
                    r.getClass().getName(), r.priority());
            }
            CACHED_RESOLVERS = discovered;
            return discovered;
        }
    }
}
