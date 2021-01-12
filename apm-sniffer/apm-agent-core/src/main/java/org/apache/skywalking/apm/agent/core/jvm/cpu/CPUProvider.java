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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.ProcessorUtil;
import org.apache.skywalking.apm.network.common.v3.CPU;

public enum CPUProvider {
    INSTANCE;
    private CPUMetricsAccessor cpuMetricsAccessor;

    CPUProvider() {
        int processorNum = ProcessorUtil.getNumberOfProcessors();
        try {
            this.cpuMetricsAccessor = (CPUMetricsAccessor) CPUProvider.class.getClassLoader()
                                                                            .loadClass("org.apache.skywalking.apm.agent.core.jvm.cpu.SunCpuAccessor")
                                                                            .getConstructor(int.class)
                                                                            .newInstance(processorNum);
        } catch (Exception e) {
            this.cpuMetricsAccessor = new NoSupportedCPUAccessor(processorNum);
            ILog logger = LogManager.getLogger(CPUProvider.class);
            logger.error(e, "Only support accessing CPU metrics in SUN JVM platform.");
        }
    }

    public CPU getCpuMetric() {
        return cpuMetricsAccessor.getCPUMetrics();
    }
}
