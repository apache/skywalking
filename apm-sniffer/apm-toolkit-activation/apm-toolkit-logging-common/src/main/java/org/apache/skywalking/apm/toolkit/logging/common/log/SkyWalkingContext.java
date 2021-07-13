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

package org.apache.skywalking.apm.toolkit.logging.common.log;

import org.apache.skywalking.apm.agent.core.conf.Config;

public class SkyWalkingContext {
    private final String serviceName = Config.Agent.SERVICE_NAME;
    private final String instanceName = Config.Agent.INSTANCE_NAME;

    private String traceId;
    private String traceSegmentId;
    private int spanId;

    public SkyWalkingContext(String traceId, String traceSegmentId, int spanId) {
        this.traceId = traceId;
        this.traceSegmentId = traceSegmentId;
        this.spanId = spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public String toString() {
        if (-1 == spanId) {
            return "[" + String.join(",", serviceName, instanceName, "N/A", "N/A", "-1") + "]";
        }
        return "[" + String.join(",", serviceName, instanceName, traceId, traceSegmentId, String.valueOf(spanId)) + "]";
    }
}
