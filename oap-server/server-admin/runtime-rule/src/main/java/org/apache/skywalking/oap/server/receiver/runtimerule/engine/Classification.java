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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

/**
 * Outcome of {@link RuleEngine#classify}. The scheduler reads this to drive the phase
 * pipeline:
 * <ul>
 *   <li>{@link #NO_CHANGE} — content byte-identical to the prior bundle. Scheduler
 *       short-circuits unless the caller forces a re-apply (e.g.
 *       {@code /addOrUpdate?force=true}, or the cold-boot ddl-debt promotion path).</li>
 *   <li>{@link #NEW} — no prior bundle for this key (or the prior bundle was an INACTIVE
 *       tombstone that's now being reactivated). Scheduler runs the full pipeline:
 *       compile → fireSchemaChanges (create) → verify → commit.</li>
 *   <li>{@link #FILTER_ONLY} — DSL body / filter / tag-assignments changed but every
 *       metric / rule key kept the same shape. Scheduler skips fireSchemaChanges + verify
 *       (no DDL needed) and goes straight to commit, swapping the in-memory bundle so the
 *       new body takes effect.</li>
 *   <li>{@link #STRUCTURAL} — at least one metric / rule key changed shape, or metrics /
 *       keys were added or removed. Scheduler runs the full pipeline.</li>
 *   <li>{@link #INACTIVE} — DB row status flipped to INACTIVE. Scheduler routes to
 *       {@link RuleEngine#unregister}; no compile or DDL fire needed for an apply.</li>
 * </ul>
 *
 * <p>Engines compute richer delta info (added / removed / shape-break sets for MAL;
 * planned rule keys for LAL) and carry it on their own {@link CompiledDSL} subclass —
 * the scheduler doesn't need to see it; only the producing engine consumes it on later
 * phases.
 */
public enum Classification {
    NO_CHANGE,
    NEW,
    FILTER_ONLY,
    STRUCTURAL,
    INACTIVE
}
