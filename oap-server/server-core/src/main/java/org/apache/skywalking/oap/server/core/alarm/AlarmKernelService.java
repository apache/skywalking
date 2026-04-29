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

package org.apache.skywalking.oap.server.core.alarm;

import java.util.Set;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Kernel operations on the alarm subsystem for cross-module callers. Named broadly so future
 * alarm-kernel operations (force-fire, pause-rule, inspect-state, etc.) can extend the same
 * interface without adding a new module contract for each.
 *
 * <p>First method: {@link #reset(Set)} — invoked by the runtime-rule hot-update pipeline at
 * the tail of a successful structural apply. When a metric name's semantics move (function
 * change, scope change, metric added or removed), any alarm rule whose expression references
 * that metric holds window values that are no longer semantically comparable to new samples;
 * a reset zeroes the window and state-machine so firing state doesn't carry across the
 * boundary.
 */
public interface AlarmKernelService extends Service {

    /**
     * Reset the evaluation window of every running alarm rule that references any of the
     * supplied metric names. Specifically: clear accumulated window values, reset the rule's
     * state-machine to OK, zero silence and recovery countdowns, and reset {@code endTime}.
     *
     * <p>Best-effort: a failure to reset a single rule logs a warn and continues — the alarm
     * subsystem self-heals within one evaluation period anyway; the reset is a quality-of-life
     * nudge to avoid false firings across the metric-semantics boundary.
     *
     * <p>No-op when {@code affectedMetricNames} is null or empty. Safe to call from any
     * thread; the implementation is expected to serialize per-rule resets with concurrent
     * sample evaluation on that rule so observers never see a torn state.
     *
     * @param affectedMetricNames metric names whose semantics just moved. Typically derived
     *                            from the runtime-rule apply pipeline's union of added /
     *                            removed / shape-changed metric sets.
     */
    void reset(Set<String> affectedMetricNames);
}
