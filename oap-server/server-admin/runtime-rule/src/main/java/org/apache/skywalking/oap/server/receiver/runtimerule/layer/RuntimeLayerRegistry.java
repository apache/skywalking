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

package org.apache.skywalking.oap.server.receiver.runtimerule.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;

/**
 * In-memory refcount tracker for runtime-DSL dynamic layer declarations. Sits between the
 * runtime-rule appliers and {@link Layer}'s post-seal {@link Layer#registerDynamic} /
 * {@link Layer#unregisterDynamic} entry points.
 *
 * <p>Lifecycle invariant: a layer is present in {@link Layer}'s registry (with ordinal
 * {@code >= RUNTIME_DYNAMIC_MIN_ORDINAL}) iff at least one runtime rule currently claims
 * it through this registry. When the last claim drops, the layer is unregistered. Layers
 * with ordinals below {@link Layer#RUNTIME_DYNAMIC_MIN_ORDINAL} are never tracked here —
 * they are owned by the bundled / boot-time channels and are non-removable.
 *
 * <p>Crash recovery: state lives in-memory only. On OAP restart the runtime-rule replay
 * path re-applies every stored runtime rule through this registry, rebuilding the same
 * refcount snapshot. No separate persistence is needed because the rule yaml itself is
 * the source of truth for layer declarations.
 *
 * <p>Cluster semantics: each OAP instance owns an independent {@code RuntimeLayerRegistry}.
 * Convergence comes from operator-pinned ordinals (same yaml on every node produces the
 * same registration) plus rule yaml replicating identically through the existing
 * runtime-rule storage. Refcount state is process-local — peers don't share the map; they
 * each track their own claims.
 *
 * <p>Thread safety: every public method is {@code synchronized} so apply / rollback /
 * teardown sequences are atomic. The registry is the single mutator of {@link Layer}'s
 * dynamic entries inside the runtime-rule module — the appliers and orchestrator must not
 * call {@link Layer#registerDynamic} directly.
 */
@Slf4j
public final class RuntimeLayerRegistry {

    /**
     * Process-wide singleton mirroring {@link Layer}'s process-singleton scope. Used by the
     * MAL/LAL engines from their compile / rollback / unregister paths. Tests that need
     * an isolated registry can construct one via {@link #RuntimeLayerRegistry()}.
     */
    public static final RuntimeLayerRegistry INSTANCE = new RuntimeLayerRegistry();

    /**
     * Claim graph: layer name → set of rule IDs that have declared this layer. A layer's
     * lifetime in {@link Layer}'s registry is bounded by this set being non-empty. Use
     * {@link LinkedHashSet} so iteration order is stable for diagnostic output.
     */
    private final Map<String, Set<String>> claimsByLayer = new HashMap<>();

    /**
     * Reverse index: rule ID → set of layer names this rule currently claims. Maintained
     * in lockstep with {@link #claimsByLayer} so a rule's prior claims can be looked up
     * in {@code O(claims)} during UPDATE.
     */
    private final Map<String, Set<String>> claimsByRule = new HashMap<>();

    /**
     * Static helper for building the canonical rule ID consumed by every method on this
     * registry. The MAL/LAL appliers receive {@code sourceName} as {@code "catalog/name"}
     * already, so the simplest path is to pass that string through verbatim — keeping the
     * registry decoupled from the {@code Catalog} enum.
     */
    public static String ruleId(final String catalog, final String name) {
        return catalog + "/" + name;
    }

    /**
     * Validate the proposed declarations against the current state, then apply them
     * atomically: register net-new layers through {@link Layer#registerDynamic}, replace
     * this rule's prior claims with {@code newClaims} in the refcount graph, and
     * unregister any layer whose last claimant just dropped.
     *
     * <p>Atomicity is per-rule: if validation throws partway, no state changes are
     * committed. If a {@link Layer#registerDynamic} call throws after some registrations
     * have already landed (race against another OAP-internal mutator — should not happen
     * under the synchronized boundary, defensive only), the partial state is rolled back
     * before re-throwing.
     *
     * @param ruleId    canonical rule identifier (use {@link #ruleId(String, String)})
     * @param newClaims the resolved layer declarations from the rule yaml (post-allocation,
     *                  post-validation of the {@code >= 100_000} ordinal floor)
     * @return {@link AppliedClaims} carrying the prior state so the caller can roll back
     *         later (e.g. on persist / commit failure outside the applier).
     * @throws LayerConflictException on any name/ordinal conflict against the current
     *         registry view (excluding this rule's own prior claims, so self-edits work).
     */
    public synchronized AppliedClaims apply(final String ruleId, final List<LayerClaim> newClaims) {
        if (ruleId == null || ruleId.isEmpty()) {
            throw new IllegalArgumentException("ruleId must be non-empty");
        }
        final Set<String> priorLayerNames = new LinkedHashSet<>(
            claimsByRule.getOrDefault(ruleId, Collections.emptySet()));
        // Snapshot BEFORE mutation — pass-1 removeClaim may unregister entries that
        // priorLayerNamesSnapshot would no longer be able to resolve.
        final Set<LayerClaim> priorClaimsSnapshot = priorLayerNamesSnapshot(priorLayerNames);

        if (newClaims != null && !newClaims.isEmpty()) {
            RuntimeLayerConflictChecker.validate(ruleId, newClaims, priorLayerNames, this);
        }

        // Diff plan computed up front so the two passes below are straight-line.
        final Set<String> newLayerNames = new LinkedHashSet<>();
        final Map<String, LayerClaim> newByName = new LinkedHashMap<>();
        if (newClaims != null) {
            for (final LayerClaim claim : newClaims) {
                newLayerNames.add(claim.getName());
                newByName.put(claim.getName(), claim);
            }
        }
        final Set<String> toRemoveNames = new LinkedHashSet<>();
        for (final LayerClaim prior : priorClaimsSnapshot) {
            final LayerClaim incoming = newByName.get(prior.getName());
            if (incoming == null || !incoming.equals(prior)) {
                toRemoveNames.add(prior.getName());
            }
        }
        final List<LayerClaim> toRegister = new ArrayList<>();
        for (final LayerClaim claim : newByName.values()) {
            final Layer existing = Layer.nameOf(claim.getName());
            final boolean sameLiveTriple = existing != Layer.UNDEFINED
                && existing.value() == claim.getOrdinal()
                && existing.isNormal() == claim.isNormal();
            if (sameLiveTriple) {
                // Soft-claim case (existing is bundled) or dynamic-and-same-triple — no
                // Layer mutation needed; refcount-add still happens in pass 2.
                continue;
            }
            toRegister.add(claim);
        }

        // Pass 1: drop prior claims. Frees ordinals so swap-style replacements in pass 2
        // can register against the now-free slot.
        for (final String name : toRemoveNames) {
            removeClaim(ruleId, name);
        }

        final Set<String> newlyRegisteredInThisApply = new LinkedHashSet<>();
        try {
            for (final LayerClaim claim : toRegister) {
                final boolean wasAbsent = Layer.nameOf(claim.getName()) == Layer.UNDEFINED;
                Layer.registerDynamic(claim.getName(), claim.getOrdinal(), claim.isNormal());
                if (wasAbsent) {
                    newlyRegisteredInThisApply.add(claim.getName());
                }
            }
            for (final LayerClaim claim : newClaims == null ? Collections.<LayerClaim>emptyList() : newClaims) {
                claimsByLayer.computeIfAbsent(claim.getName(), k -> new LinkedHashSet<>())
                             .add(ruleId);
            }
        } catch (final RuntimeException e) {
            // Defence-in-depth: synchronized + post-validate should make this unreachable.
            for (final String name : newlyRegisteredInThisApply) {
                try {
                    Layer.unregisterDynamic(name);
                } catch (final RuntimeException ignore) {
                }
            }
            for (final LayerClaim prior : priorClaimsSnapshot) {
                try {
                    Layer.registerDynamic(prior.getName(), prior.getOrdinal(), prior.isNormal());
                } catch (final RuntimeException ignore) {
                }
                claimsByLayer.computeIfAbsent(prior.getName(), k -> new LinkedHashSet<>())
                             .add(ruleId);
            }
            // Restore reverse index.
            claimsByRule.put(ruleId, new LinkedHashSet<>(priorLayerNames));
            throw e;
        }

        // Reverse index — final state matches new claims; intermediate Pass-1 mutations
        // already cleaned out the prior names.
        if (newLayerNames.isEmpty()) {
            claimsByRule.remove(ruleId);
        } else {
            claimsByRule.put(ruleId, newLayerNames);
        }

        return new AppliedClaims(ruleId, priorClaimsSnapshot, newLayerNames,
                                 newlyRegisteredInThisApply);
    }

    /**
     * Undo the effect of an earlier {@link #apply(String, List)}: restore this rule's
     * prior claims and unregister any layer that had been registered net-new by the apply
     * and is no longer claimed.
     *
     * <p>Used by the orchestrator on persist / commit failure (after the applier had
     * already succeeded). For in-applier failures the apply method's own internal
     * rollback handles cleanup before re-throwing — callers do NOT need to invoke this
     * for that case.
     *
     * @param applied bundle returned from the matching {@link #apply} call
     */
    public synchronized void rollback(final AppliedClaims applied) {
        if (applied == null) {
            return;
        }
        final String ruleId = applied.getRuleId();

        // Drop the newly-claimed entries that the apply added.
        for (final String name : applied.getCurrentLayerNames()) {
            removeClaim(ruleId, name);
        }

        // Restore prior claims (re-registering layers if the apply had freed them).
        for (final LayerClaim priorClaim : applied.getPriorClaims()) {
            try {
                Layer.registerDynamic(priorClaim.getName(), priorClaim.getOrdinal(),
                                      priorClaim.isNormal());
            } catch (final RuntimeException e) {
                // Best-effort: the prior claim's layer might already be registered
                // (idempotent re-register handles same-triple). Log and continue so the
                // refcount graph stays correct even if one entry fails to re-register.
                log.warn("runtime-layer rollback: failed to re-register prior claim {}",
                         priorClaim, e);
            }
            claimsByLayer.computeIfAbsent(priorClaim.getName(), k -> new LinkedHashSet<>())
                         .add(ruleId);
            claimsByRule.computeIfAbsent(ruleId, k -> new LinkedHashSet<>())
                         .add(priorClaim.getName());
        }

        if (!claimsByRule.containsKey(ruleId)
            || claimsByRule.get(ruleId).isEmpty()) {
            claimsByRule.remove(ruleId);
        }
    }

    /**
     * Drop every claim owned by {@code ruleId}. Called by the unregister path on DELETE /
     * INACTIVATE — the rule is gone, so its layers must release their refcount slot, and
     * any layer whose last claimant just dropped is unregistered through
     * {@link Layer#unregisterDynamic}.
     */
    public synchronized void removeRule(final String ruleId) {
        final Set<String> owned = claimsByRule.remove(ruleId);
        if (owned == null || owned.isEmpty()) {
            return;
        }
        for (final String name : owned) {
            final Set<String> claimants = claimsByLayer.get(name);
            if (claimants == null) {
                continue;
            }
            claimants.remove(ruleId);
            if (claimants.isEmpty()) {
                claimsByLayer.remove(name);
                if (Layer.isDynamic(name)) {
                    try {
                        Layer.unregisterDynamic(name);
                    } catch (final RuntimeException e) {
                        log.warn("runtime-layer removeRule: unregister threw for {} "
                                     + "(unexpected — Layer.isDynamic was true)", name, e);
                    }
                }
                // else: soft claim (bundled-tier owner). Refcount entry dropped; the
                // bundled registration stays per the layer's lifecycle contract.
            }
        }
    }

    /**
     * Snapshot of layer names this rule currently claims. Returns an empty unmodifiable
     * set if the rule has no claims. Used by the conflict checker to exclude self-edits
     * from conflict probes.
     */
    public synchronized Set<String> claimsOf(final String ruleId) {
        final Set<String> owned = claimsByRule.get(ruleId);
        if (owned == null || owned.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(owned));
    }

    /**
     * Snapshot of all currently-claimed runtime dynamic layers. The map key is the layer
     * name; the value is the set of rules that have declared it. Used by the conflict
     * checker for source labelling ("runtime rule otel-rules/foo also declares this").
     */
    public synchronized Map<String, Set<String>> snapshot() {
        final Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, Set<String>> e : claimsByLayer.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Lookup helper for the conflict checker: which rules claim this layer right now? */
    synchronized Set<String> claimantsOf(final String layerName) {
        final Set<String> claimants = claimsByLayer.get(layerName);
        if (claimants == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(claimants));
    }

    /** Remove {@code ruleId} from {@code layerName}'s claimants and from the reverse index;
     *  if the claimants set empties, delete the entry and unregister the layer IF the
     *  layer was registered through the dynamic channel. Soft claims (a runtime rule
     *  whose triple matches an existing bundled / boot-time layer — see the conflict
     *  checker's case 3a) do NOT unregister: the bundled tier owns the layer's lifecycle
     *  and only a restart-time removal of the boot-time source can drop it. */
    private void removeClaim(final String ruleId, final String layerName) {
        final Set<String> claimants = claimsByLayer.get(layerName);
        if (claimants != null) {
            claimants.remove(ruleId);
            if (claimants.isEmpty()) {
                claimsByLayer.remove(layerName);
                if (Layer.isDynamic(layerName)) {
                    try {
                        Layer.unregisterDynamic(layerName);
                    } catch (final RuntimeException e) {
                        log.warn("runtime-layer apply: unregister threw for {} (unexpected — "
                                     + "Layer.isDynamic was true)", layerName, e);
                    }
                }
                // else: non-dynamic layer (bundled / boot-time extension). Soft claim
                // released; layer stays in the registry per the bundled-tier contract.
            }
        }
        // Keep the reverse index in lockstep — without this, rollback re-adds prior
        // claims on top of the (still-stale) currentLayerNames entries.
        final Set<String> owned = claimsByRule.get(ruleId);
        if (owned != null) {
            owned.remove(layerName);
            if (owned.isEmpty()) {
                claimsByRule.remove(ruleId);
            }
        }
    }

    /**
     * Re-resolve full prior claims (with ordinal/normal) from layer names. Used in
     * {@link #apply} to build the prior-state component of {@link AppliedClaims} so
     * {@link #rollback} can restore the exact triples. Walks {@link Layer}'s registry
     * (lookups by name) because the registry is the live source of truth for
     * {@code (ordinal, normal)}.
     */
    private Set<LayerClaim> priorLayerNamesSnapshot(final Set<String> priorLayerNames) {
        if (priorLayerNames.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<LayerClaim> out = new LinkedHashSet<>();
        for (final String name : priorLayerNames) {
            final Layer existing = Layer.nameOf(name);
            if (existing == Layer.UNDEFINED) {
                // Shouldn't happen — we tracked this rule as claiming `name`, so it must
                // have been registered. Skip rather than throw so rollback can proceed
                // for the other entries.
                log.warn("runtime-layer snapshot: prior-claimed layer {} not in registry "
                             + "(skipping during rollback bundle)", name);
                continue;
            }
            out.add(new LayerClaim(existing.name(), existing.value(), existing.isNormal()));
        }
        return Collections.unmodifiableSet(out);
    }
}
