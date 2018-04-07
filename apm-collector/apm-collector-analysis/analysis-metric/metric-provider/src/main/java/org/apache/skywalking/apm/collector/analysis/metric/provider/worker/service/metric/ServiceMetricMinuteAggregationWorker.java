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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.metric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricMinuteAggregationWorker extends AggregationWorker<ServiceReferenceMetric, ServiceMetric> {

    private ServiceMetricMinuteAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.SERVICE_MINUTE_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected ServiceMetric transform(ServiceReferenceMetric serviceReferenceMetric) {
        Integer serviceId = serviceReferenceMetric.getBehindServiceId();
        Long timeBucket = serviceReferenceMetric.getTimeBucket();
        Integer sourceValue = serviceReferenceMetric.getSourceValue();

        String metricId = String.valueOf(serviceId) + Const.ID_SPLIT + String.valueOf(sourceValue);
        String id = String.valueOf(timeBucket) + Const.ID_SPLIT + metricId;

        ServiceMetric serviceMetric = new ServiceMetric();
        serviceMetric.setId(id);
        serviceMetric.setMetricId(metricId);

        serviceMetric.setApplicationId(serviceReferenceMetric.getBehindApplicationId());
        serviceMetric.setInstanceId(serviceReferenceMetric.getBehindInstanceId());
        serviceMetric.setServiceId(serviceId);
        serviceMetric.setSourceValue(sourceValue);

        serviceMetric.setTransactionCalls(serviceReferenceMetric.getTransactionCalls());
        serviceMetric.setTransactionDurationSum(serviceReferenceMetric.getTransactionDurationSum());
        serviceMetric.setTransactionErrorCalls(serviceReferenceMetric.getTransactionErrorCalls());
        serviceMetric.setTransactionErrorDurationSum(serviceReferenceMetric.getTransactionErrorDurationSum());

        serviceMetric.setBusinessTransactionCalls(serviceReferenceMetric.getBusinessTransactionCalls());
        serviceMetric.setBusinessTransactionDurationSum(serviceReferenceMetric.getBusinessTransactionDurationSum());
        serviceMetric.setBusinessTransactionErrorCalls(serviceReferenceMetric.getBusinessTransactionErrorCalls());
        serviceMetric.setBusinessTransactionErrorDurationSum(serviceReferenceMetric.getBusinessTransactionErrorDurationSum());

        serviceMetric.setMqTransactionCalls(serviceReferenceMetric.getMqTransactionCalls());
        serviceMetric.setMqTransactionDurationSum(serviceReferenceMetric.getMqTransactionDurationSum());
        serviceMetric.setMqTransactionErrorCalls(serviceReferenceMetric.getMqTransactionErrorCalls());
        serviceMetric.setMqTransactionErrorDurationSum(serviceReferenceMetric.getMqTransactionErrorDurationSum());

        serviceMetric.setTimeBucket(timeBucket);

        return serviceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceReferenceMetric, ServiceMetric, ServiceMetricMinuteAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceMetricMinuteAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceMetricMinuteAggregationWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }

    @GraphComputingMetric(name = "/aggregate/onWork/" + ServiceMetricTable.TABLE)
    @Override protected void onWork(ServiceReferenceMetric message) throws WorkerException {
        super.onWork(message);
    }
}
