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

package org.apache.skywalking.oap.server.receiver.runtimerule.reconcile;

import org.apache.skywalking.oap.server.receiver.runtimerule.apply.LalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal.LalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.mal.MalRuleEngine;

/**
 * Pure key-format helpers shared across the runtime-rule dslManager, REST handler, and
 * cluster service. Lives outside {@link DSLManager} so consumers can reference these
 * without pulling in the orchestrator type.
 */
public final class DSLScriptKey {

    private DSLScriptKey() {
    }

    /**
     * Snapshot key for a (catalog, name) pair. Same format every node uses, so cluster
     * Suspend / Resume / Forward RPCs and REST {@code /list} resolve to the same entry
     * the dslManager owns.
     */
    public static String key(final String catalog, final String name) {
        return catalog + ":" + name;
    }

    /**
     * Stringify a {@link LalFileApplier.RegisteredRule} into a {@code "layer:ruleName"}
     * key used by the LAL apply-path diff to identify which old rule keys are
     * truly-gone (not taken over via {@code factory.addOrReplace}) and therefore need
     * explicit removal. Auto-layer rules serialize as the literal string "auto" to
     * match how the rest of the LAL path represents them.
     */
    public static String lalRuleKey(final LalFileApplier.RegisteredRule r) {
        final String layer = r.getLayer() == null ? "auto" : r.getLayer().name();
        return layer + ":" + r.getRuleName();
    }

    /**
     * First eight characters of a SHA-256 hex string, or {@code "none"} when the input
     * is null. Used in log breadcrumbs where the full digest would be noise but
     * operators still want enough discriminator to match an apply log line to its
     * stored row.
     */
    public static String shortHash(final String hash) {
        if (hash == null || hash.length() <= 8) {
            return hash == null ? "none" : hash;
        }
        return hash.substring(0, 8);
    }

    /**
     * True for catalogs whose rule files parse as MAL. Routes through the engine registry
     * rather than a hardcoded string set so a catalog added to {@link MalRuleEngine#supportedCatalogs}
     * (e.g. {@code telegraf-rules}) is automatically recognised by every {@code isMalCatalog}
     * caller — no parallel string list to keep in sync.
     */
    public static boolean isMalCatalog(final RuleEngineRegistry registry, final String catalog) {
        final RuleEngine<?> engine = registry.forCatalog(catalog);
        return engine instanceof MalRuleEngine;
    }

    /** True for catalogs whose rule files parse as LAL. Same registry-driven routing as
     *  {@link #isMalCatalog}. */
    public static boolean isLalCatalog(final RuleEngineRegistry registry, final String catalog) {
        final RuleEngine<?> engine = registry.forCatalog(catalog);
        return engine instanceof LalRuleEngine;
    }
}
