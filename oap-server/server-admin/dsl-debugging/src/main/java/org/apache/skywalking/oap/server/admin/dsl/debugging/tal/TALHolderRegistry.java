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

package org.apache.skywalking.oap.server.admin.dsl.debugging.tal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.DebugHolderLookup;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TalStaticBindingHook;

/**
 * The TAL rules this node loaded, as {@link TalStaticBindingHook} publishes them at boot: their capture bindings for
 * the session install path, and every rule, debuggable or not, for the read-only rule listing.
 */
@Slf4j
public final class TALHolderRegistry implements DebugHolderLookup {

    private final ConcurrentHashMap<RuleKey, GateHolder> holders = new ConcurrentHashMap<>();
    private final List<TalStaticBindingHook.Binding> bindings = new CopyOnWriteArrayList<>();
    /**
     * The keys two rules share. A session names a rule by its file and name, while a file may hold rules of one name
     * in different layers; such a key picks neither, rather than whichever loaded last.
     */
    private final Set<RuleKey> shared = ConcurrentHashMap.newKeySet();

    public void register(final TalStaticBindingHook.Binding binding) {
        bindings.add(binding);
        if (binding.getHolder() != null) {
            final RuleKey key = new RuleKey(Catalog.TAL, binding.getName(), binding.getRuleName());
            if (shared.contains(key) || holders.putIfAbsent(key, binding.getHolder()) != null) {
                shared.add(key);
                holders.remove(key);
                log.warn("Two TAL rules of {} are named {}; a debug session names a rule by its file and name, so "
                    + "neither can be debugged until one is renamed", binding.getFile(), binding.getRuleName());
            }
        }
        log.debug("TAL holder registry: registered {} of {}", binding.getRuleName(), binding.getFile());
    }

    /**
     * @return every loaded rule in the order it loaded: the {@code talFiles} rules, then the native rules in the
     * order they run
     */
    public List<TalStaticBindingHook.Binding> bindings() {
        return Collections.unmodifiableList(new ArrayList<>(bindings));
    }

    @Override
    public boolean serves(final RuleKey key) {
        return key != null && key.getCatalog() == Catalog.TAL;
    }

    @Override
    public GateHolder lookup(final RuleKey key) {
        return holders.get(key);
    }
}
