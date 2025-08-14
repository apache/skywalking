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
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

@Slf4j
public class MetricsPersistentMinOALWorker extends MetricsPersistentMinWorker {

    MetricsPersistentMinOALWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                                  AbstractWorker<Metrics> nextAlarmWorker, AbstractWorker<ExportEvent> nextExportWorker,
                                  MetricsTransWorker transWorker, boolean supportUpdate,
                                  long storageSessionTimeout, int metricsDataTTL, MetricStreamKind kind) {
        super(
            moduleDefineHolder, model, metricsDAO, nextAlarmWorker, nextExportWorker, transWorker, supportUpdate,
            storageSessionTimeout, metricsDataTTL, kind,
            "METRICS_L2_AGGREGATION_OAL",
            BulkConsumePool.Creator.recommendMaxSize() / 8 == 0 ? 1 : BulkConsumePool.Creator.recommendMaxSize() / 8,
            false,
            1,
            2000
        );
    }
}
