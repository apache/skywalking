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

package org.apache.skywalking.oap.server.admin.dsl.debugging.mal;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Live registry of MAL {@link RuleKey} → {@link GateHolder} bindings.
 * Implements {@link DebugHolderLookup} so the session registry can resolve
 * a holder via the same uniform contract LAL / OAL will satisfy in their
 * own phases.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   Static rule loaders + runtime-rule MAL apply path
 *      └─ register(ruleKey, gateHolder)           on every successful compile
 *      └─ unregister(ruleKey)                     on inactivate / delete / hot-update
 *
 *   DebugSessionRegistry.install
 *      └─ asks every registered DebugHolderLookup
 *      └─ this registry returns the live GateHolder
 *         (or null if the rule has been unregistered since the session request
 *         arrived — manifests as 404 at the REST layer)
 * </pre>
 *
 * <p>Hot-update interaction: a runtime-rule swap calls
 * {@code register(key, newHolder)} which replaces the entry; new sessions
 * bind to the new holder. Pre-update sessions stay on their original
 * holder (the {@code DebugSession} captured it at install time), so the
 * V1 binding still drains correctly until the V1 class goes out of scope.
 *
 * <p>Storing the {@link GateHolder} directly (rather than the
 * {@code MalExpression} that exposes it) keeps this module's API surface
 * to {@code server-core} types only — no leak of the analyzer's
 * compiled-rule type into the registry interface.
 *
 * <p>Exposed as a {@link Service} so this module's provider can publish
 * one process-wide instance and the static / runtime-rule paths can both
 * resolve it through the module manager.
 */
@Slf4j
public final class MALHolderRegistry implements DebugHolderLookup, Service {

    private final ConcurrentHashMap<RuleKey, GateHolder> bindings = new ConcurrentHashMap<>();

    public void register(final RuleKey key, final GateHolder holder) {
        if (key == null || holder == null) {
            return;
        }
        bindings.put(key, holder);
        log.debug("MAL holder registry: registered {}", key);
    }

    public void unregister(final RuleKey key) {
        if (key == null) {
            return;
        }
        if (bindings.remove(key) != null) {
            log.debug("MAL holder registry: unregistered {}", key);
        }
    }

    @Override
    public boolean serves(final RuleKey key) {
        if (key == null) {
            return false;
        }
        final Catalog c = key.getCatalog();
        return c == Catalog.OTEL_RULES
            || c == Catalog.LOG_MAL_RULES
            || c == Catalog.TELEGRAF_RULES;
    }

    @Override
    public GateHolder lookup(final RuleKey key) {
        return bindings.get(key);
    }

    /** Visible for tests. */
    public int size() {
        return bindings.size();
    }
}
