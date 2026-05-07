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

package org.apache.skywalking.oap.server.core.dsldebug;

/**
 * Implemented by every OAL dispatcher class so the holder-lookup path can
 * resolve a per-metric {@link GateHolder} with a typed call instead of a
 * reflective {@code Class#getMethod} probe.
 *
 * <p>OAL gates are per-metric — each {@code do<Metric>()} method has its
 * own {@code debug_<metric>} field. {@link #debugHolder(String)} returns
 * the holder for the requested metric or {@code null} when the dispatcher
 * doesn't carry a rule with that name. {@link #debugRuleNames()} enumerates
 * the metric names this dispatcher knows about — used by the read-only OAL
 * listing endpoint.
 *
 * <p>MAL and LAL artefacts don't implement this directly because they live
 * behind their own per-rule {@code MalExpression.debugHolder()} /
 * {@code LalExpression.debugHolder()} accessors; the registry caches those
 * holders on commit instead of asking the artefact on every install.
 */
public interface DebugHolderProvider {
    /**
     * The holder for a specific OAL metric this dispatcher routes. Returns
     * {@code null} when {@code metricName} doesn't match any rule on the
     * dispatcher (typo, wrong file, etc.) or when no holder is wired (mock
     * dispatchers in tests, dispatchers that didn't go through the
     * debug-aware codegen path).
     */
    GateHolder debugHolder(String metricName);

    /**
     * Read-only metric-name set this dispatcher routes. Used by the DSL
     * debug API's read-only OAL listing endpoint to render a "source X
     * routes metrics A, B, C" view alongside the picker.
     */
    String[] debugRuleNames();
}
