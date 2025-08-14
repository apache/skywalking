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

package org.apache.skywalking.oap.server.core.analysis.worker;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * MetricsAggregateMALWorker provides an in-memory metrics merging capability for MAL
 */
@Slf4j
public class MetricsAggregateMALWorker extends MetricsAggregateWorker {
    private final static String POOL_NAME = "METRICS_L1_AGGREGATION_MAL";
    private final BulkConsumePool pool;

    MetricsAggregateMALWorker(ModuleDefineHolder moduleDefineHolder,
                              AbstractWorker<Metrics> nextWorker,
                              String modelName,
                              long l1FlushPeriod,
                              MetricStreamKind kind) {
        // In MAL meter streaming, the load of data flow is much less as they are statistics already,
        // but in OAL sources, they are raw data.
        // Set the buffer(size of queue) as 1/20 to reduce unnecessary resource costs.
        super(
            moduleDefineHolder, nextWorker, modelName, l1FlushPeriod, kind,
            POOL_NAME,
            BulkConsumePool.Creator.recommendMaxSize() / 8 == 0 ? 1 : BulkConsumePool.Creator.recommendMaxSize() / 8,
            true,
            1,
            1_000
        );
        this.pool = (BulkConsumePool) ConsumerPoolFactory.INSTANCE.get(POOL_NAME);
    }

    /**
     * MetricsAggregateWorker#in operation does include enqueue only
     */
    @Override
    public final void in(Metrics metrics) {
        super.in(metrics);
        pool.notifyConsumers();
    }
}