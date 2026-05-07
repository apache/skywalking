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

package org.apache.skywalking.oap.server.admin.dsl.debugging.oal;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.core.analysis.DispatcherManager;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.DebugHolderProvider;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * OAL counterpart of {@code MALHolderRegistry} — but stateless. OAL holders
 * live on the dispatcher singletons that {@code OALClassGeneratorV2} mints
 * once at boot, not on a per-rule runtime artifact that comes and goes.
 * The {@link DispatcherManager} already maintains the dispatcher set; this
 * lookup walks it asking each dispatcher for the holder of the requested
 * metric via {@link DebugHolderProvider#debugHolder(String)}.
 *
 * <p>OAL gates are per-metric: the {@link RuleKey#getRuleName()} carries
 * the metric name (e.g. {@code "service_relation_server_cpm"}). The
 * {@link RuleKey#getName()} carries the {@code .oal} file the rule lives
 * in (e.g. {@code "core.oal"}) — informational today since the dispatcher
 * walk is global, but the file name pins the rule's identity for
 * hot-update tracking.
 *
 * <p>Because OAL doesn't have hot-update today (rules are baked into
 * dispatchers at boot), there is nothing to register or unregister — the
 * holder lifetime equals the OAP process lifetime.
 */
@RequiredArgsConstructor
public final class OALHolderLookup implements DebugHolderLookup {

    private final DispatcherManager dispatcherManager;

    @Override
    public boolean serves(final RuleKey key) {
        return key != null && key.getCatalog() == Catalog.OAL;
    }

    @Override
    public GateHolder lookup(final RuleKey key) {
        return dispatcherManager.debugHolderForOalMetric(key.getRuleName());
    }
}
