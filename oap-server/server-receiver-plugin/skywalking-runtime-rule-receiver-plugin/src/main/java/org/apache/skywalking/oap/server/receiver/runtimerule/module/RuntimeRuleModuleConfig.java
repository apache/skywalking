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
     * Default {@code 0.0.0.0}. Once the module is enabled via its selector, it binds on this
     * address. Expose only behind a gateway / IP allow-list — never to the public internet.
     */
    private String restHost = "0.0.0.0";
    /** Default {@code 17128}. Runtime-rule admin HTTP endpoint. */
    private int restPort = 17128;
    private String restContextPath = "/";
    private int restIdleTimeOut = 30_000;
    private int restAcceptQueueSize = 0;
    private int httpMaxRequestHeaderSize = 8192;
    /** DSLManager tick interval in seconds. 30 s is the documented convergence bound. */
    private long reconcilerIntervalSeconds = 30;
    /**
     * SUSPENDED state self-heal threshold in seconds. Must exceed dslManager tick + ES
     * refresh + storage replica lag + RPC jitter. Default 60 s is conservative.
     */
    private long selfHealThresholdSeconds = 60;
}
