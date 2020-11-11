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

import java.util.Objects;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * MetricsTransWorker is transferring the metrics for downsampling. All streaming process metrics are in the minute
 * precision, but at the storage layer, in order to enhance the query performance, metrics could be saved in minute,
 * hour, day and month, including some of them through CoreModuleConfig#downsampling.
 */
public class MetricsTransWorker extends AbstractWorker<Metrics> {
    private final MetricsPersistentWorker hourPersistenceWorker;
    private final MetricsPersistentWorker dayPersistenceWorker;

    public MetricsTransWorker(ModuleDefineHolder moduleDefineHolder,
                              MetricsPersistentWorker hourPersistenceWorker,
                              MetricsPersistentWorker dayPersistenceWorker) {
        super(moduleDefineHolder);
        this.hourPersistenceWorker = hourPersistenceWorker;
        this.dayPersistenceWorker = dayPersistenceWorker;
    }

    /**
     * Use the {@link Metrics#toHour()} and {@link Metrics#toDay()}to clone a new metrics instance then process the
     * downsampling. Then forward the data to different works of different precisions for another round
     * aggregation/merging.
     */
    @Override
    public void in(Metrics metrics) {
        if (Objects.nonNull(hourPersistenceWorker)) {
            hourPersistenceWorker.in(metrics.toHour());
        }
        if (Objects.nonNull(dayPersistenceWorker)) {
            dayPersistenceWorker.in(metrics.toDay());
        }
    }
}
