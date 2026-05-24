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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.server.core.analysis.Layer;

/**
 * Pure validation helper for runtime-DSL layer declarations. Produces operator-actionable
 * {@link LayerConflictException}s that the REST handler translates 1:1 into the
 * {@code {applyStatus, message}} response envelope. Every message names the offending
 * layer, the conflicting ordinal, and (where applicable) the source that already owns the
 * name, so the operator's corrective action is obvious from the response body alone.
 *
 * <p>Validation is performed against the live {@link Layer} registry, masking out the
 * caller rule's prior claims so a same-rule edit (e.g. changing a runtime layer's ordinal
 * from {@code 100_001} to {@code 100_002}) is permitted without an
 * inactivate-then-recreate round trip.
 */
public final class RuntimeLayerConflictChecker {

    /**
     * Same regex enforced by {@link Layer#registerDynamic}; mirrored here so the operator
     * gets the dedicated {@code layer_name_invalid} status (mapped to HTTP 400) instead
     * of an {@code IllegalArgumentException} masquerading as a 500.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private RuntimeLayerConflictChecker() {
    }

    /**
     * Validate {@code candidates} against the current {@link Layer} registry and the
     * given {@link RuntimeLayerRegistry}, treating the caller rule's {@code priorClaims}
     * as already-removed (because the in-flight apply will replace them). Throws on the
     * first detected conflict; partial applies are impossible because the caller invokes
     * this BEFORE any {@link Layer#registerDynamic} call lands.
     *
     * @param ruleId        canonical rule identifier — surfaced in conflict messages so
     *                      the operator can pinpoint the source side of a cross-rule
     *                      conflict
     * @param candidates    proposed layer declarations (ordinal already validated to
     *                      {@code >= RUNTIME_DYNAMIC_MIN_ORDINAL} by the applier; this
     *                      method re-checks for defence in depth)
     * @param priorClaims   layer names this rule currently claims; conflicts against
     *                      these are NOT raised, since the apply will release them
     * @param refcounts     refcount tracker used to label runtime-side conflict sources
     *                      ("runtime rule otel-rules/foo also declares X")
     * @throws LayerConflictException on the first conflict
     */
    public static void validate(final String ruleId,
                                final List<LayerClaim> candidates,
                                final Set<String> priorClaims,
                                final RuntimeLayerRegistry refcounts) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        // Pre-pass: detect duplicates within the same batch BEFORE any registry check.
        // Two entries naming the same layer with different triples, or two entries pinning
        // the same ordinal under different names, cannot both succeed even in isolation;
        // raising early keeps apply() atomic — without this pre-pass, a per-claim loop
        // would mutate the registry for entry N before throwing on entry N+1, leaving a
        // half-applied state that the rollback path then has to unwind.
        final Map<String, LayerClaim> byName = new HashMap<>(candidates.size());
        final Map<Integer, LayerClaim> byOrdinal = new HashMap<>(candidates.size());
        for (final LayerClaim claim : candidates) {
            final LayerClaim sameName = byName.get(claim.getName());
            if (sameName != null && !sameName.equals(claim)) {
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_NAME_CONFLICT,
                    "Batch in rule " + ruleId + " declares layer '" + claim.getName()
                        + "' twice with different triples (" + sameName + " vs " + claim
                        + "). A single batch must declare each layer at most once.");
            }
            final LayerClaim sameOrdinal = byOrdinal.get(claim.getOrdinal());
            if (sameOrdinal != null && !sameOrdinal.getName().equals(claim.getName())) {
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_ORDINAL_COLLISION,
                    "Batch in rule " + ruleId + " pins ordinal " + claim.getOrdinal()
                        + " under two different names ('" + sameOrdinal.getName()
                        + "' and '" + claim.getName() + "'). Each ordinal must be unique "
                        + "within a single layerDefinitions block.");
            }
            byName.put(claim.getName(), claim);
            byOrdinal.put(claim.getOrdinal(), claim);
        }
        for (final LayerClaim claim : candidates) {
            validateOne(ruleId, claim, priorClaims, refcounts);
        }
    }

    private static void validateOne(final String ruleId,
                                    final LayerClaim claim,
                                    final Set<String> priorClaims,
                                    final RuntimeLayerRegistry refcounts) {
        // 1. Name shape.
        if (claim.getName() == null || !NAME_PATTERN.matcher(claim.getName()).matches()) {
            throw new LayerConflictException(
                LayerConflictException.Status.LAYER_NAME_INVALID,
                "Layer name '" + claim.getName() + "' is invalid — must match "
                    + "[A-Z][A-Z0-9_]* (upper-snake-case, leading letter, no spaces).");
        }

        // 2. Ordinal range.
        if (claim.getOrdinal() < Layer.RUNTIME_DYNAMIC_MIN_ORDINAL) {
            throw new LayerConflictException(
                LayerConflictException.Status.LAYER_ORDINAL_OUT_OF_RANGE,
                "Layer '" + claim.getName() + "' declared with ordinal " + claim.getOrdinal()
                    + " in rule " + ruleId + ". Runtime DSL layers must pin an explicit "
                    + "ordinal in the runtime tier (>= " + Layer.RUNTIME_DYNAMIC_MIN_ORDINAL
                    + "). Reserved ranges: 0-9999 = built-in Layer constants, "
                    + "10_000-99_999 = layer-extensions.yml + bundled MAL/LAL "
                    + "layerDefinitions. Omitting 'ordinal:' (default 0) is rejected "
                    + "because the ordinal is persisted in ServiceTraffic primary keys "
                    + "and must be operator-stable across restarts.");
        }

        // 3. Name conflict. Non-dynamic existing + same triple = soft claim (refcount
        // tracks the runtime rule but Layer stays bundled). Anything else against a
        // non-dynamic owner is a hard conflict. Against a dynamic owner, self-claims
        // are permitted; cross-rule and multi-claimant triple changes are not.
        final Layer byName = Layer.nameOf(claim.getName());
        if (byName != Layer.UNDEFINED) {
            final boolean tripleMatches =
                byName.value() == claim.getOrdinal() && byName.isNormal() == claim.isNormal();
            if (!Layer.isDynamic(claim.getName())) {
                if (tripleMatches) {
                    // Soft claim — apply refcount-adds without calling registerDynamic.
                    return;
                }
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_NAME_CONFLICT,
                    "Layer '" + claim.getName() + "' is already registered by "
                        + sourceLabel(claim.getName(), refcounts)
                        + " as (ordinal=" + byName.value()
                        + ", normal=" + byName.isNormal()
                        + ") through the boot-time channel; rule " + ruleId
                        + " declares (ordinal=" + claim.getOrdinal()
                        + ", normal=" + claim.isNormal() + "). Align the runtime "
                        + "declaration to match, or rename the runtime layer.");
            }
            if (!tripleMatches) {
                final boolean priorlyClaimedByThisRule =
                    priorClaims != null && priorClaims.contains(claim.getName());
                if (priorlyClaimedByThisRule) {
                    // Self-update is only legal if this rule is the sole claimant.
                    final Set<String> claimants = refcounts.claimantsOf(claim.getName());
                    if (claimants.size() > 1) {
                        throw new LayerConflictException(
                            LayerConflictException.Status.LAYER_NAME_CONFLICT,
                            "Layer '" + claim.getName() + "' is also claimed by other "
                                + "runtime rule(s) "
                                + String.join(", ", otherClaimants(claimants, ruleId))
                                + " at (ordinal=" + byName.value()
                                + ", normal=" + byName.isNormal()
                                + "); rule " + ruleId + " cannot change the triple to "
                                + "(ordinal=" + claim.getOrdinal()
                                + ", normal=" + claim.isNormal()
                                + ") because the change would affect the other claimants. "
                                + "Remove the prior declaration from this rule first, or "
                                + "have every claimant align on the new triple in lockstep.");
                    }
                    // Sole claimant — apply will replace the prior triple. Fall through.
                } else {
                    throw new LayerConflictException(
                        LayerConflictException.Status.LAYER_NAME_CONFLICT,
                        "Layer '" + claim.getName() + "' already registered by "
                            + sourceLabel(claim.getName(), refcounts)
                            + " as (ordinal=" + byName.value()
                            + ", normal=" + byName.isNormal()
                            + "); rule " + ruleId + " declares (ordinal=" + claim.getOrdinal()
                            + ", normal=" + claim.isNormal()
                            + "). Align both declarations or rename one.");
                }
            } else {
                // Same name and same triple — idempotent re-declare (dynamic only,
                // because the !isDynamic branch above already rejected the bundled case).
                return;
            }
        }

        // 4. Ordinal collision — different name, same ordinal already used by someone.
        // Sub-cases mirror step 3: bundled holders are immovable; dynamic holders allow
        // a self-update where this rule is freeing the ordinal.
        final Layer byOrdinal = Layer.peekByValue(claim.getOrdinal());
        if (byOrdinal != null && !byOrdinal.name().equals(claim.getName())) {
            if (!Layer.isDynamic(byOrdinal.name())) {
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_ORDINAL_COLLISION,
                    "Layer '" + claim.getName() + "' ordinal " + claim.getOrdinal()
                        + " is already used by layer '" + byOrdinal.name()
                        + "' (registered by " + sourceLabel(byOrdinal.name(), refcounts)
                        + " through the boot-time channel). Pick a different ordinal "
                        + "(>= " + Layer.RUNTIME_DYNAMIC_MIN_ORDINAL + ").");
            }
            final boolean priorlyHeldByThisRule =
                priorClaims != null && priorClaims.contains(byOrdinal.name());
            if (!priorlyHeldByThisRule) {
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_ORDINAL_COLLISION,
                    "Layer '" + claim.getName() + "' ordinal " + claim.getOrdinal()
                        + " is already used by layer '" + byOrdinal.name()
                        + "' (registered by " + sourceLabel(byOrdinal.name(), refcounts)
                        + "). Pick a different ordinal (>= "
                        + Layer.RUNTIME_DYNAMIC_MIN_ORDINAL + ").");
            }
            // Self-update freeing the ordinal is only legal if this rule is the sole
            // claimant of the prior layer. Other claimants would still hold the slot
            // after apply Pass-1 drops THIS rule's claim, so registerDynamic would
            // throw an unstructured collision deep inside the apply path.
            final Set<String> otherHolders = refcounts.claimantsOf(byOrdinal.name());
            if (otherHolders.size() > 1) {
                throw new LayerConflictException(
                    LayerConflictException.Status.LAYER_ORDINAL_COLLISION,
                    "Layer '" + claim.getName() + "' ordinal " + claim.getOrdinal()
                        + " is still held by layer '" + byOrdinal.name()
                        + "' under other runtime rule(s) "
                        + String.join(", ", otherClaimants(otherHolders, ruleId))
                        + "; rule " + ruleId + " cannot reclaim the ordinal alone. "
                        + "Either drop the prior layer from this rule with the others "
                        + "in lockstep, or pick a different ordinal.");
            }
        }
    }

    /** Filter {@code claimants} to entries other than {@code self}. Used in self-update
     *  conflict messages to name only the rules whose declaration would be impacted. */
    private static List<String> otherClaimants(final Set<String> claimants, final String self) {
        final List<String> out = new ArrayList<>(claimants.size());
        for (final String c : claimants) {
            if (!c.equals(self)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Label the source that owns a layer's current registration. Granularity follows the
     * {@link Layer#isDynamic} channel, NOT the ordinal range — operator yaml may put a
     * boot-time layer anywhere the registry accepts, including the {@code >=100_000}
     * tier; labelling those as "runtime DSL" would mislead the operator into thinking
     * they could realign at runtime.
     * <ul>
     *   <li>{@code "built-in or boot-time extension"} — registered through
     *       {@link Layer#register} (built-in {@code Layer.*} constants,
     *       {@code layer-extensions.yml}, bundled MAL/LAL {@code layerDefinitions:}).</li>
     *   <li>{@code "runtime rule(s) <id1>, <id2>, ..."} — registered through
     *       {@link Layer#registerDynamic}, listing every runtime rule currently claiming
     *       the name through the refcount tracker.</li>
     * </ul>
     */
    private static String sourceLabel(final String name, final RuntimeLayerRegistry refcounts) {
        final Layer layer = Layer.nameOf(name);
        if (layer == Layer.UNDEFINED) {
            return "(unknown source — registry inconsistency)";
        }
        if (!Layer.isDynamic(name)) {
            return "built-in or boot-time extension";
        }
        final Set<String> claimants = refcounts.claimantsOf(name);
        if (claimants.isEmpty()) {
            // Edge case: layer is in the runtime tier but not tracked by refcount. Should
            // not happen under the synchronized boundary; log-and-fall-back if it does.
            return "runtime DSL (untracked)";
        }
        return "runtime rule(s) " + String.join(", ", claimants);
    }
}
