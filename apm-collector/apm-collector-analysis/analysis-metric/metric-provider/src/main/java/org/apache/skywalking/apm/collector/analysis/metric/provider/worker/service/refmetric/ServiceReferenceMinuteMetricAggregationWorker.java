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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMinuteMetricAggregationWorker extends AggregationWorker<ServiceReferenceMetric, ServiceReferenceMetric> {

    private ServiceReferenceMinuteMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.SERVICE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceReferenceMetric, ServiceReferenceMetric, ServiceReferenceMinuteMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceReferenceMinuteMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceReferenceMinuteMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/aggregate/onWork/" + ServiceReferenceMetricTable.TABLE)
    @Override protected void onWork(ServiceReferenceMetric message) throws WorkerException {
        super.onWork(message);
    }
}
