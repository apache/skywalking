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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * The session install path consults this contract to resolve the live
 * {@link GateHolder} for a given {@link RuleKey}. Each DSL provider (MAL,
 * LAL, OAL) registers a lookup with the {@link DebugSessionRegistry} so a
 * session installation looks up the holder without a per-DSL conditional
 * inside the registry — the registry stays DSL-agnostic, which is what
 * lets phases 2 and 3 add LAL / OAL without touching this code.
 *
 * <p>Catalog-keyed dispatch is enforced by the registry: each lookup
 * declares which catalogs it serves via {@link #serves(RuleKey)} and the
 * registry asks every registered lookup until one returns a non-null holder.
 *
 * <p>Returning {@code null} means "this rule key is not currently bound to a
 * live artifact" — the registry surfaces that as a 404 to the REST caller.
 * Hot-update / inactivate windows are an expected source of {@code null}:
 * the rule has been retired locally; trying to install a session on it
 * should fail fast rather than bind to a stale holder.
 */
public interface DebugHolderLookup {

    /**
     * @return {@code true} if this lookup can resolve holders for this rule
     *         key's catalog. The registry calls every {@link DebugHolderLookup}
     *         registered for the relevant DSL, but this gate keeps cross-DSL
     *         lookups quiet (e.g. an MAL lookup never sees an OAL rule).
     */
    boolean serves(RuleKey key);

    /**
     * Resolve the live holder for the given rule key. {@code null} means
     * "no live binding right now"; the registry will translate that into
     * a {@code 404} response.
     */
    GateHolder lookup(RuleKey key);
}
