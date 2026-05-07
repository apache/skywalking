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

package org.apache.skywalking.oap.server.admin.dsl.debugging.lal;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Live registry of LAL {@link RuleKey} → {@link GateHolder} bindings —
 * the LAL counterpart of {@code MALHolderRegistry}. The runtime-rule LAL
 * apply path publishes / unpublishes bindings as rules are compiled and
 * hot-updated; the session registry resolves them via the
 * {@link DebugHolderLookup} contract.
 */
@Slf4j
public final class LALHolderRegistry implements DebugHolderLookup, Service {

    private final ConcurrentHashMap<RuleKey, GateHolder> bindings = new ConcurrentHashMap<>();

    public void register(final RuleKey key, final GateHolder holder) {
        if (key == null || holder == null) {
            return;
        }
        bindings.put(key, holder);
        log.debug("LAL holder registry: registered {}", key);
    }

    public void unregister(final RuleKey key) {
        if (key == null) {
            return;
        }
        if (bindings.remove(key) != null) {
            log.debug("LAL holder registry: unregistered {}", key);
        }
    }

    @Override
    public boolean serves(final RuleKey key) {
        return key != null && key.getCatalog() == Catalog.LAL;
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
