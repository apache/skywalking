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

package org.apache.skywalking.oap.server.receiver.runtimerule.extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.rule.ext.RuntimeRuleOverrideResolver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Serves operator-supplied rule overrides (rows in the {@code runtime_rule} management table)
 * to MAL / LAL static-file loaders at boot, so a reboot never regresses to the pre-override
 * rule body on disk. Discovered via {@link java.util.ServiceLoader} (declared in
 * {@code META-INF/services/org.apache.skywalking.oap.server.core.rule.ext.RuntimeRuleOverrideResolver}).
 *
 * <h2>Priority</h2>
 * Returns {@code 100} so this resolver wins over any priority-0 default resolver but loses to
 * higher-priority sources operators might add later (GitOps, k8s ConfigMap, etc.). Pick a
 * higher number on a more authoritative resolver to override DB content at boot.
 *
 * <h2>Caching</h2>
 * On the first {@link #loadAll} call per catalog, this implementation pulls every
 * {@link RuntimeRuleManagementDAO.RuntimeRuleFile} once from storage and caches the catalog
 * result in memory. Subsequent calls are pure in-memory lookups. The cache is per-process
 * lifetime; new runtime changes after boot are driven by the runtime-rule reconciler tick
 * via its own apply path, not through this static-load hook.
 *
 * <h2>Behaviour per row</h2>
 * <ul>
 *   <li>{@code ACTIVE}   → {@link RuntimeRuleOverrideResolver.Resolution#active(byte[])} —
 *       merger replaces the disk content with the DB content.</li>
 *   <li>{@code INACTIVE} → {@link RuntimeRuleOverrideResolver.Resolution#inactive()} —
 *       merger removes the entry, even if disk has a file for the same key.</li>
 * </ul>
 *
 * <h2>Failure modes</h2>
 * <ul>
 *   <li>{@code manager} is {@code null}: caller has no module context (tests). Returns an
 *       empty map; the merger leaves the disk baseline untouched.</li>
 *   <li>Storage module not installed / DAO service not exposed: log INFO once, cache an empty
 *       map. Boot proceeds with pure static content.</li>
 *   <li>Storage read throws: log WARN, cache nothing for this catalog so the next call retries
 *       (handles boot-time transient failures — runtime_rule table not yet created, gRPC pool
 *       warming, ES template still being applied).</li>
 * </ul>
 */
@Slf4j
public final class DbOverrideRuntimeRuleResolver implements RuntimeRuleOverrideResolver {

    /**
     * Per-catalog cache of resolutions. Keyed by catalog name (e.g. {@code "otel-rules"});
     * value is the resolver's full opinion for that catalog. Populated on first
     * {@link #loadAll} per catalog. A transient failure leaves the entry absent so the next
     * call retries; a permanent absence (no DAO service) caches an empty map.
     */
    private final Map<String, Map<String, Resolution>> cache = new HashMap<>();

    public DbOverrideRuntimeRuleResolver() {
        // Required public no-arg constructor for ServiceLoader instantiation.
    }

    @Override
    public int priority() {
        // Baseline runtime-rule priority. Higher-priority sources (e.g. GitOps watchers) can
        // override these by registering their own resolver with a larger priority value.
        return 100;
    }

    @Override
    public Map<String, Resolution> loadAll(final String catalog, final ModuleManager manager) {
        if (manager == null) {
            // Test path or static-loader call without module context — nothing we can do.
            return Collections.emptyMap();
        }
        synchronized (this) {
            final Map<String, Resolution> cached = cache.get(catalog);
            if (cached != null) {
                return cached;
            }
            final Map<String, Resolution> loaded = load(catalog, manager);
            if (loaded != null) {
                // Permanent answer (success OR DAO-unavailable cached empty); promote to cache.
                cache.put(catalog, loaded);
                return loaded;
            }
            // Transient failure: don't cache, return empty so this call's static file loads
            // from disk; the next call will retry the DAO read.
            return Collections.emptyMap();
        }
    }

    /**
     * One-shot DAO load + classification for a catalog.
     *
     * @return populated map (possibly empty) on success or permanent absence; {@code null}
     *         on a transient failure that the caller should not cache.
     */
    private Map<String, Resolution> load(final String catalog, final ModuleManager manager) {
        final RuntimeRuleManagementDAO dao;
        try {
            dao = manager.find(StorageModule.NAME).provider().getService(RuntimeRuleManagementDAO.class);
        } catch (final Throwable t) {
            // Permanent absence — runtime-rule plugin disabled, storage module shape mismatch,
            // etc. Cache empty so we don't keep retrying for the rest of the process lifetime.
            log.info("RuntimeRuleManagementDAO unavailable ({}); runtime-rule overrides will not "
                + "apply to static boot load this run.", t.getMessage());
            return Collections.emptyMap();
        }
        final List<RuntimeRuleManagementDAO.RuntimeRuleFile> rows;
        try {
            rows = dao.getAll();
        } catch (final IOException ioe) {
            log.warn("Failed to read runtime_rule rows at boot for catalog {}; will retry on the "
                + "next loadAll call. Static files load from disk for this pass.", catalog, ioe);
            return null;
        }
        final Map<String, Resolution> result = new HashMap<>();
        for (final RuntimeRuleManagementDAO.RuntimeRuleFile row : rows) {
            if (!catalog.equals(row.getCatalog())) {
                continue;
            }
            if ("INACTIVE".equalsIgnoreCase(row.getStatus())) {
                result.put(row.getName(), Resolution.inactive());
            } else {
                // STATUS_ACTIVE (default) or any non-INACTIVE status — treat as active substitution.
                final byte[] bytes = row.getContent() == null
                    ? new byte[0]
                    : row.getContent().getBytes(StandardCharsets.UTF_8);
                result.put(row.getName(), Resolution.active(bytes));
            }
        }
        log.info("Runtime-rule boot resolver loaded {} override(s) for catalog {}", result.size(), catalog);
        return result;
    }
}
