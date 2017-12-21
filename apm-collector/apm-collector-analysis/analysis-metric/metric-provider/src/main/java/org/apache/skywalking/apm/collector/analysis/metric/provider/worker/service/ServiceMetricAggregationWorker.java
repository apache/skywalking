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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricAggregationWorker extends AggregationWorker<ServiceReferenceMetric, ServiceMetric> {

    public ServiceMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return WorkerIdDefine.SERVICE_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected ServiceMetric transform(ServiceReferenceMetric serviceReferenceMetric) {
        Integer serviceId = serviceReferenceMetric.getBehindServiceId();
        Long timeBucket = serviceReferenceMetric.getTimeBucket();
        ServiceMetric serviceMetric = new ServiceMetric(String.valueOf(timeBucket) + Const.ID_SPLIT + String.valueOf(serviceId));
        serviceMetric.setServiceId(serviceId);

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

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceReferenceMetric, ServiceMetric, ServiceMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceMetricAggregationWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
