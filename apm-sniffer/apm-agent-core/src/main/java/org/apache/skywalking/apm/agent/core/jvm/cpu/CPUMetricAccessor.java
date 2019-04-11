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


package org.apache.skywalking.apm.agent.core.jvm.cpu;

import org.apache.skywalking.apm.network.common.CPU;
import org.apache.skywalking.apm.network.language.agent.*;

/**
 * @author wusheng
 */
public abstract class CPUMetricAccessor {
    private long lastCPUTimeNs;
    private long lastSampleTimeNs;
    private final int cpuCoreNum;

    public CPUMetricAccessor(int cpuCoreNum) {
        this.cpuCoreNum = cpuCoreNum;
    }

    protected void init() {
        lastCPUTimeNs = this.getCpuTime();
        this.lastSampleTimeNs = System.nanoTime();
    }

    protected abstract long getCpuTime();

    public CPU getCPUMetric() {
        long cpuTime = this.getCpuTime();
        long cpuCost = cpuTime - lastCPUTimeNs;
        long now = System.nanoTime();

        CPU.Builder cpuBuilder = CPU.newBuilder();
        return cpuBuilder.setUsagePercent(cpuCost * 1.0d / ((now - lastSampleTimeNs) * cpuCoreNum) * 100).build();
    }
}
