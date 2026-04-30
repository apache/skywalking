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

import java.util.Map;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Boot-time resolver SPI consulted by MAL / LAL static-file loaders. Each implementation
 * contributes its own view of "what rules should be live for a catalog at boot" — a DB
 * resolver in the runtime-rule plugin, a future GitOps resolver in another plugin, etc.
 *
 * <h2>Discovery</h2>
 * Loaded via {@link java.util.ServiceLoader}. Plugins ship a
 * {@code META-INF/services/org.apache.skywalking.oap.server.core.rule.ext.RuntimeRuleOverrideResolver}
 * line per implementation. Implementations MUST have a public no-arg constructor.
 *
 * <h2>Merge semantics</h2>
 * {@link RuleSetMerger} folds the disk view + every resolver's {@link #loadAll} into a single
 * {@code (name -> bytes)} map per catalog, with priority deciding ties:
 * <ul>
 *   <li>Lower {@link #priority} resolvers are applied first; higher priority overwrites.</li>
 *   <li>{@link Decision#ACTIVE} substitutes the resolver's content into the merged set.</li>
 *   <li>{@link Decision#INACTIVE} removes the entry from the merged set — even if the disk
 *       file or a lower-priority resolver had content for that key.</li>
 *   <li>A resolver omits a key from {@link #loadAll} when it has no opinion; the next higher
 *       priority resolver (or the disk baseline) is the source of truth.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>
 *   Resolver A (priority 100, runtime-rule DB):
 *     "vm"          =&gt; Resolution(ACTIVE,    bytes-from-DB)
 *     "noisy-rule"  =&gt; Resolution(INACTIVE, null)
 *
 *   Result for catalog "otel-rules":
 *     - if disk has "vm.yaml":          merged["vm"]          = bytes-from-DB
 *     - if disk has "noisy-rule.yaml":  merged drops "noisy-rule" entirely
 *     - if disk lacks "new-rule.yaml":  merged["new-rule"]    = bytes-from-DB (DB-only rule)
 * </pre>
 */
public interface RuntimeRuleOverrideResolver {

    /**
     * Resolver priority. Higher number wins on conflict (last-write-wins under
     * descending-priority application). Default {@code 0}.
     *
     * <p>Suggested ranges:
     * <ul>
     *   <li>{@code 0–99}    — defaults, low-trust sources</li>
     *   <li>{@code 100}     — runtime-rule DB ({@code DbOverrideRuntimeRuleResolver})</li>
     *   <li>{@code 200–999} — externally-managed config (GitOps, k8s ConfigMap, etc.)</li>
     * </ul>
     * Resolvers with equal priority are applied in classpath / ServiceLoader iteration
     * order — explicit priority is preferred over relying on that.
     *
     * @return higher = stronger override.
     */
    default int priority() {
        return 0;
    }

    /**
     * Every {@code (name, Resolution)} this resolver wants to contribute for the given
     * catalog. Names not present in the returned map mean "I have no opinion" — the
     * merge engine leaves the disk baseline (or a lower-priority resolver's contribution)
     * in place for those keys.
     *
     * <p>Implementations are expected to cache. The {@code manager} reference lets a
     * resolver look up services it needs (e.g. {@code RuntimeRuleManagementDAO} via
     * the storage module). It may be {@code null} when called from test paths or from
     * loaders that don't have a module context — resolvers that need a manager should
     * return an empty map in that case rather than throw.
     *
     * @param catalog one of {@code "otel-rules"}, {@code "log-mal-rules"}, {@code "lal"},
     *                or a future catalog name. Resolvers should return an empty map for
     *                catalogs they don't recognise.
     * @param manager OAP module manager, or {@code null} when the caller has no module
     *                context (tests).
     * @return per-name Resolution; never {@code null} (return an empty map instead).
     */
    Map<String, Resolution> loadAll(String catalog, ModuleManager manager);

    /**
     * Per-key decision. Names follow the {@code RuntimeRule} status enum so wire vocabulary
     * stays consistent across the API surface (REST endpoint statuses, DB column values,
     * resolver decisions).
     */
    enum Decision {
        /** This resolver wants the rule live with the supplied content. */
        ACTIVE,
        /** This resolver wants the rule removed regardless of disk content. */
        INACTIVE
    }

    /**
     * One resolver's opinion about a single rule. Immutable.
     */
    final class Resolution {
        private final Decision decision;
        private final byte[] content;

        public Resolution(final Decision decision, final byte[] content) {
            this.decision = decision;
            this.content = content;
        }

        /**
         * Convenience constructor for {@link Decision#ACTIVE} resolutions — the only kind
         * that carries content.
         */
        public static Resolution active(final byte[] content) {
            return new Resolution(Decision.ACTIVE, content);
        }

        /**
         * Convenience for {@link Decision#INACTIVE} resolutions — content is null.
         */
        public static Resolution inactive() {
            return new Resolution(Decision.INACTIVE, null);
        }

        public Decision getDecision() {
            return decision;
        }

        /**
         * Raw rule bytes when {@link #getDecision()} is {@link Decision#ACTIVE}; {@code null}
         * for {@link Decision#INACTIVE}.
         */
        public byte[] getContent() {
            return content;
        }
    }
}
