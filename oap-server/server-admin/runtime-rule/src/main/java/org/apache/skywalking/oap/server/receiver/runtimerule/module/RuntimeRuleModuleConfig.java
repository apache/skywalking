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

package org.apache.skywalking.oap.server.receiver.runtimerule.module;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class RuntimeRuleModuleConfig extends ModuleConfig {
    /**
     * Period (seconds) of the runtime-rule refresh tick on each OAP node. Every tick
     * the node re-reads the stored rule rows from the storage DAO and reconciles its
     * local state against the persisted truth — catch-up for nodes that missed a
     * Suspend / Resume / Forward broadcast (network glitch, restart, late-joining
     * peer), and static fall-over reload when a runtime row was deleted (the bundled
     * rule comes back live without a manual nudge). 30 s is the documented
     * convergence bound.
     */
    private long refreshRulesPeriod = 30;
    /**
     * SUSPENDED state self-heal threshold in seconds. Must exceed dslManager tick + ES
     * refresh + storage replica lag + RPC jitter. Default 60 s is conservative.
     */
    private long selfHealThresholdSeconds = 60;
}
