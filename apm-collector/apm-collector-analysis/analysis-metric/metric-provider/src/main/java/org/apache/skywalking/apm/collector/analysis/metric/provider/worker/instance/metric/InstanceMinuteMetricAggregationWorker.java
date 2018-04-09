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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.metric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceMinuteMetricAggregationWorker extends AggregationWorker<InstanceReferenceMetric, InstanceMetric> {

    private InstanceMinuteMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.INSTANCE_MINUTE_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected InstanceMetric transform(InstanceReferenceMetric instanceReferenceMetric) {
        String metricId = instanceReferenceMetric.getBehindInstanceId() + Const.ID_SPLIT + instanceReferenceMetric.getSourceValue();
        String id = instanceReferenceMetric.getTimeBucket() + Const.ID_SPLIT + metricId;

        InstanceMetric instanceMetric = new InstanceMetric();
        instanceMetric.setId(id);
        instanceMetric.setMetricId(metricId);

        instanceMetric.setApplicationId(instanceReferenceMetric.getBehindApplicationId());
        instanceMetric.setInstanceId(instanceReferenceMetric.getBehindInstanceId());
        instanceMetric.setSourceValue(instanceReferenceMetric.getSourceValue());

        instanceMetric.setTransactionCalls(instanceReferenceMetric.getTransactionCalls());
        instanceMetric.setTransactionErrorCalls(instanceReferenceMetric.getTransactionErrorCalls());
        instanceMetric.setTransactionDurationSum(instanceReferenceMetric.getTransactionDurationSum());
        instanceMetric.setTransactionErrorDurationSum(instanceReferenceMetric.getTransactionErrorDurationSum());

        instanceMetric.setBusinessTransactionCalls(instanceReferenceMetric.getBusinessTransactionCalls());
        instanceMetric.setBusinessTransactionErrorCalls(instanceReferenceMetric.getBusinessTransactionErrorCalls());
        instanceMetric.setBusinessTransactionDurationSum(instanceReferenceMetric.getBusinessTransactionDurationSum());
        instanceMetric.setBusinessTransactionErrorDurationSum(instanceReferenceMetric.getBusinessTransactionErrorDurationSum());

        instanceMetric.setMqTransactionCalls(instanceReferenceMetric.getMqTransactionCalls());
        instanceMetric.setMqTransactionErrorCalls(instanceReferenceMetric.getMqTransactionErrorCalls());
        instanceMetric.setMqTransactionDurationSum(instanceReferenceMetric.getMqTransactionDurationSum());
        instanceMetric.setMqTransactionErrorDurationSum(instanceReferenceMetric.getMqTransactionErrorDurationSum());

        instanceMetric.setTimeBucket(instanceReferenceMetric.getTimeBucket());
        return instanceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceReferenceMetric, InstanceMetric, InstanceMinuteMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public InstanceMinuteMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceMinuteMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/aggregate/onWork/" + InstanceMetricTable.TABLE)
    @Override protected void onWork(InstanceReferenceMetric message) throws WorkerException {
        super.onWork(message);
    }
}
