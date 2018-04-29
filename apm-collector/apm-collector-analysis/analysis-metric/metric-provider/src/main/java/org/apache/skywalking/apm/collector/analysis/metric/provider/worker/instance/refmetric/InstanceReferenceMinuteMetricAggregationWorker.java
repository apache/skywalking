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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.refmetric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMinuteMetricAggregationWorker extends AggregationWorker<ServiceReferenceMetric, InstanceReferenceMetric> {

    private InstanceReferenceMinuteMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.INSTANCE_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected InstanceReferenceMetric transform(ServiceReferenceMetric serviceReferenceMetric) {
        String metricId = serviceReferenceMetric.getFrontInstanceId()
            + Const.ID_SPLIT + serviceReferenceMetric.getBehindInstanceId()
            + Const.ID_SPLIT + serviceReferenceMetric.getSourceValue();

        String id = serviceReferenceMetric.getTimeBucket()
            + Const.ID_SPLIT + metricId;

        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric();
        instanceReferenceMetric.setId(id);
        instanceReferenceMetric.setMetricId(metricId);
        instanceReferenceMetric.setFrontApplicationId(serviceReferenceMetric.getFrontApplicationId());
        instanceReferenceMetric.setFrontInstanceId(serviceReferenceMetric.getFrontInstanceId());
        instanceReferenceMetric.setBehindApplicationId(serviceReferenceMetric.getBehindApplicationId());
        instanceReferenceMetric.setBehindInstanceId(serviceReferenceMetric.getBehindInstanceId());
        instanceReferenceMetric.setSourceValue(serviceReferenceMetric.getSourceValue());

        instanceReferenceMetric.setTransactionCalls(serviceReferenceMetric.getTransactionCalls());
        instanceReferenceMetric.setTransactionErrorCalls(serviceReferenceMetric.getTransactionErrorCalls());
        instanceReferenceMetric.setTransactionDurationSum(serviceReferenceMetric.getTransactionDurationSum());
        instanceReferenceMetric.setTransactionErrorDurationSum(serviceReferenceMetric.getTransactionErrorDurationSum());

        instanceReferenceMetric.setBusinessTransactionCalls(serviceReferenceMetric.getBusinessTransactionCalls());
        instanceReferenceMetric.setBusinessTransactionErrorCalls(serviceReferenceMetric.getBusinessTransactionErrorCalls());
        instanceReferenceMetric.setBusinessTransactionDurationSum(serviceReferenceMetric.getBusinessTransactionDurationSum());
        instanceReferenceMetric.setBusinessTransactionErrorDurationSum(serviceReferenceMetric.getBusinessTransactionErrorDurationSum());

        instanceReferenceMetric.setMqTransactionCalls(serviceReferenceMetric.getMqTransactionCalls());
        instanceReferenceMetric.setMqTransactionErrorCalls(serviceReferenceMetric.getMqTransactionErrorCalls());
        instanceReferenceMetric.setMqTransactionDurationSum(serviceReferenceMetric.getMqTransactionDurationSum());
        instanceReferenceMetric.setMqTransactionErrorDurationSum(serviceReferenceMetric.getMqTransactionErrorDurationSum());

        instanceReferenceMetric.setTimeBucket(serviceReferenceMetric.getTimeBucket());
        return instanceReferenceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceReferenceMetric, InstanceReferenceMetric, InstanceReferenceMinuteMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public InstanceReferenceMinuteMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceReferenceMinuteMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/aggregate/onWork/" + InstanceReferenceMetricTable.TABLE)
    @Override protected void onWork(ServiceReferenceMetric message) throws WorkerException {
        super.onWork(message);
    }
}
