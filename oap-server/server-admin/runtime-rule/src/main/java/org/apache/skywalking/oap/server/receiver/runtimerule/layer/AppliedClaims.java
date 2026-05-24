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

import java.util.Collections;
import java.util.Set;
import lombok.Getter;

/**
 * Snapshot of what a {@link RuntimeLayerRegistry#apply} call did, sufficient for
 * {@link RuntimeLayerRegistry#rollback} to undo the change. Returned to the applier so it
 * can hand the bundle to the orchestrator alongside the {@code Applied} artifact — the
 * structural commit coordinator calls back into the registry with this token on persist /
 * commit-tail failure.
 *
 * <p>{@link #getNewlyRegistered} is informational (which layers were brand-new to the
 * registry, not just newly-claimed by this rule). Rollback uses the {@code priorClaims}
 * and {@code currentLayerNames} diff to restore the exact state without needing the
 * net-new set.
 */
@Getter
public final class AppliedClaims {

    private final String ruleId;
    private final Set<LayerClaim> priorClaims;
    private final Set<String> currentLayerNames;
    private final Set<String> newlyRegistered;

    AppliedClaims(final String ruleId,
                  final Set<LayerClaim> priorClaims,
                  final Set<String> currentLayerNames,
                  final Set<String> newlyRegistered) {
        this.ruleId = ruleId;
        this.priorClaims = priorClaims == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(priorClaims);
        this.currentLayerNames = currentLayerNames == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(currentLayerNames);
        this.newlyRegistered = newlyRegistered == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(newlyRegistered);
    }

    /** Convenience: true when the apply effected no actual change (empty old and new). */
    public boolean isNoOp() {
        return priorClaims.isEmpty() && currentLayerNames.isEmpty();
    }
}
