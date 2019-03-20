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
import org.apache.skywalking.apm.network.common.CPU;
import org.apache.skywalking.apm.network.language.agent.*;

/**
 * @author wusheng
 */
public enum CPUProvider {
    INSTANCE;
    private CPUMetricAccessor cpuMetricAccessor;

    CPUProvider() {
        int processorNum = ProcessorUtil.getNumberOfProcessors();
        try {
            this.cpuMetricAccessor =
                (CPUMetricAccessor)CPUProvider.class.getClassLoader().loadClass("org.apache.skywalking.apm.agent.core.jvm.cpu.SunCpuAccessor")
                    .getConstructor(int.class).newInstance(processorNum);
        } catch (Exception e) {
            this.cpuMetricAccessor = new NoSupportedCPUAccessor(processorNum);
            ILog logger = LogManager.getLogger(CPUProvider.class);
            logger.error(e, "Only support accessing CPU metric in SUN JVM platform.");
        }
    }

    public CPU getCpuMetric() {
        return cpuMetricAccessor.getCPUMetric();
    }
}
