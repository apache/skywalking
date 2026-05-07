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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Catalog → {@link RuleEngine} lookup. Built once at module start, read on every
 * apply / unregister call. Engines self-declare their catalogs via {@link
 * RuleEngine#supportedCatalogs()}; the registry indexes them so the scheduler can
 * route a {@link org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO.RuntimeRuleFile}
 * to the right engine in O(1).
 *
 * <p>Adding a new DSL is one line in {@link
 * org.apache.skywalking.oap.server.receiver.runtimerule.module.RuntimeRuleModuleProvider}:
 * register the engine instance with this registry. No scheduler edit required.
 */
public final class RuleEngineRegistry {
    private final Map<String, RuleEngine<?>> byCatalog = new HashMap<>();

    /**
     * Register {@code engine} for every catalog it claims. Throws {@link IllegalStateException}
     * on duplicate catalog: two engines competing for the same catalog is a configuration error
     * worth failing module start over rather than silently dropping one.
     */
    public void register(final RuleEngine<?> engine) {
        for (final String catalog : engine.supportedCatalogs()) {
            final RuleEngine<?> prior = byCatalog.put(catalog, engine);
            if (prior != null && prior != engine) {
                throw new IllegalStateException(
                    "Duplicate RuleEngine registration for catalog '" + catalog
                        + "': " + prior.getClass().getName() + " vs " + engine.getClass().getName());
            }
        }
    }

    /**
     * @return the engine registered for {@code catalog}, or {@code null} if none. The scheduler
     *     treats {@code null} as a hard error (catalog should never be loaded if no engine claims
     *     it — the static rule registry filters by supported catalog at boot).
     */
    public RuleEngine<?> forCatalog(final String catalog) {
        return byCatalog.get(catalog);
    }

    /** All distinct engines, for module-start logging and lifecycle wiring. */
    public Collection<RuleEngine<?>> engines() {
        return byCatalog.values();
    }
}
