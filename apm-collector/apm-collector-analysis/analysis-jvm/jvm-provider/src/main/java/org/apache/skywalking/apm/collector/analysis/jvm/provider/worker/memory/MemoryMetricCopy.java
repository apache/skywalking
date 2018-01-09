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

package org.apache.skywalking.apm.collector.analysis.jvm.provider.worker.memory;

import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetric;

/**
 * @author peng-yongsheng
 */
public class MemoryMetricCopy {

    public static MemoryMetric copy(MemoryMetric memoryMetric) {
        MemoryMetric newMemoryMetric = new MemoryMetric();
        newMemoryMetric.setId(memoryMetric.getId());
        newMemoryMetric.setMetricId(memoryMetric.getMetricId());

        newMemoryMetric.setInstanceId(memoryMetric.getInstanceId());
        newMemoryMetric.setIsHeap(memoryMetric.getIsHeap());

        newMemoryMetric.setInit(memoryMetric.getInit());
        newMemoryMetric.setMax(memoryMetric.getMax());
        newMemoryMetric.setUsed(memoryMetric.getUsed());
        newMemoryMetric.setCommitted(memoryMetric.getCommitted());
        newMemoryMetric.setTimes(memoryMetric.getTimes());

        newMemoryMetric.setTimeBucket(memoryMetric.getTimeBucket());
        return newMemoryMetric;
    }
}
