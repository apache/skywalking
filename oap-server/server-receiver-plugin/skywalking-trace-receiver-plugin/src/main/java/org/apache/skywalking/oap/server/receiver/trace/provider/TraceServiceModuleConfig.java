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

package org.apache.skywalking.oap.server.receiver.trace.provider;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class TraceServiceModuleConfig extends ModuleConfig {
    /**
     * The sample rate precision is 1/10000. 10000 means 100% sample in default.
     */
    @Setter
    @Getter
    private int sampleRate = 10000;
    /**
     * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
     * service instance mapping and service instance client side relation are ignored.
     *
     * Read component-libraries.yml for more details.
     */
    @Getter
    private final List<Integer> noUpstreamRealAddressAgents = Collections.singletonList(6000);
    /**
     * The threshold used to check the slow database access. Unit, millisecond.
     */
    @Setter
    @Getter
    private String slowDBAccessThreshold = "default:200";
    @Setter
    @Getter
    private DBLatencyThresholdsAndWatcher dbLatencyThresholdsAndWatcher;
    @Setter
    @Getter
    private UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig;
    /**
     * Analysis trace status.
     * <p>
     * 1. Default(YES) means analysis all metrics from trace.
     * <p>
     * 2. NO means, only save trace, but metrics come other places, such as service mesh.
     */
    @Setter
    @Getter
    private boolean traceAnalysis = true;
    /**
     * Slow Sql string length can't beyond this limit
     */
    @Setter
    @Getter
    private int maxSlowSQLLength = 2000;
}
