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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.instance;

import org.apache.skywalking.apm.collector.agent.stream.service.graph.InstanceGraphNodeIdDefine;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.impl.AggregationWorker;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricAggregationWorker extends AggregationWorker<ServiceReferenceMetric, InstanceReferenceMetric> {

    public InstanceReferenceMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return InstanceGraphNodeIdDefine.INSTANCE_REFERENCE_METRIC_AGGREGATION_NODE_ID;
    }

    @Override protected InstanceReferenceMetric transform(ServiceReferenceMetric serviceReferenceMetric) {
        String id = serviceReferenceMetric.getTimeBucket() + Const.ID_SPLIT + serviceReferenceMetric.getFrontInstanceId() + Const.ID_SPLIT + serviceReferenceMetric.getBehindInstanceId();

        InstanceReferenceMetric instanceReferenceMetric = new InstanceReferenceMetric(id);
        instanceReferenceMetric.setFrontInstanceId(serviceReferenceMetric.getFrontInstanceId());
        instanceReferenceMetric.setBehindInstanceId(serviceReferenceMetric.getBehindInstanceId());

        instanceReferenceMetric.setTransactionCalls(serviceReferenceMetric.getTransactionCalls());
        instanceReferenceMetric.setTransactionErrorCalls(serviceReferenceMetric.getTransactionErrorCalls());
        instanceReferenceMetric.setTransactionDurationSum(serviceReferenceMetric.getTransactionDurationSum());
        instanceReferenceMetric.setTransactionErrorDurationSum(serviceReferenceMetric.getTransactionErrorDurationSum());

        instanceReferenceMetric.setBusinessTransactionCalls(serviceReferenceMetric.getBusinessTransactionCalls());
        instanceReferenceMetric.setBusinessTransactionErrorCalls(instanceReferenceMetric.getBusinessTransactionErrorCalls());
        instanceReferenceMetric.setBusinessTransactionDurationSum(instanceReferenceMetric.getBusinessTransactionDurationSum());
        instanceReferenceMetric.setBusinessTransactionErrorDurationSum(instanceReferenceMetric.getBusinessTransactionErrorDurationSum());

        instanceReferenceMetric.setMqTransactionCalls(instanceReferenceMetric.getMqTransactionCalls());
        instanceReferenceMetric.setMqTransactionErrorCalls(instanceReferenceMetric.getMqTransactionErrorCalls());
        instanceReferenceMetric.setMqTransactionDurationSum(instanceReferenceMetric.getMqTransactionDurationSum());
        instanceReferenceMetric.setMqTransactionErrorDurationSum(instanceReferenceMetric.getMqTransactionErrorDurationSum());

        instanceReferenceMetric.setSourceValue(serviceReferenceMetric.getSourceValue());
        return instanceReferenceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceReferenceMetric, InstanceReferenceMetric, InstanceReferenceMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<ServiceReferenceMetric> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public InstanceReferenceMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceReferenceMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
