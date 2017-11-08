/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.stream.worker.jvm;

import org.skywalking.apm.collector.core.graph.Next;
import org.skywalking.apm.collector.core.graph.NodeProcessor;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricPersistenceWorker implements NodeProcessor<MemoryPoolMetric, MemoryPoolMetric> {

    @Override public int id() {
        return 0;
    }

    @Override public void process(MemoryPoolMetric INPUT, Next<MemoryPoolMetric> next) {

    }
}
