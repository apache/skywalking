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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.application;

import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.agent.stream.service.graph.ApplicationGraphNodeIdDefine;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.impl.AggregationWorker;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricAggregationWorker extends AggregationWorker<InstanceReferenceMetric, ApplicationReferenceMetric> {

    private final InstanceCacheService instanceCacheService;

    public ApplicationReferenceMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
    }

    @Override public int id() {
        return ApplicationGraphNodeIdDefine.APPLICATION_REFERENCE_METRIC_AGGREGATION_NODE_ID;
    }

    @Override protected ApplicationReferenceMetric transform(InstanceReferenceMetric instanceReferenceMetric) {
        Integer frontApplicationId = instanceCacheService.get(instanceReferenceMetric.getFrontInstanceId());
        Integer behindApplicationId = instanceCacheService.get(instanceReferenceMetric.getBehindInstanceId());

        String id = instanceReferenceMetric.getTimeBucket() + Const.ID_SPLIT + frontApplicationId + Const.ID_SPLIT + behindApplicationId;

        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric(id);
        applicationReferenceMetric.setFrontApplicationId(frontApplicationId);
        applicationReferenceMetric.setBehindApplicationId(behindApplicationId);

        applicationReferenceMetric.setTransactionCalls(instanceReferenceMetric.getTransactionCalls());
        applicationReferenceMetric.setTransactionErrorCalls(instanceReferenceMetric.getTransactionErrorCalls());
        applicationReferenceMetric.setTransactionDurationSum(instanceReferenceMetric.getTransactionDurationSum());
        applicationReferenceMetric.setTransactionErrorDurationSum(instanceReferenceMetric.getTransactionErrorDurationSum());

        applicationReferenceMetric.setBusinessTransactionCalls(instanceReferenceMetric.getBusinessTransactionCalls());
        applicationReferenceMetric.setBusinessTransactionErrorCalls(instanceReferenceMetric.getBusinessTransactionErrorCalls());
        applicationReferenceMetric.setBusinessTransactionDurationSum(instanceReferenceMetric.getBusinessTransactionDurationSum());
        applicationReferenceMetric.setBusinessTransactionErrorDurationSum(instanceReferenceMetric.getBusinessTransactionErrorDurationSum());

        applicationReferenceMetric.setMqTransactionCalls(instanceReferenceMetric.getMqTransactionCalls());
        applicationReferenceMetric.setMqTransactionErrorCalls(instanceReferenceMetric.getMqTransactionErrorCalls());
        applicationReferenceMetric.setMqTransactionDurationSum(instanceReferenceMetric.getMqTransactionDurationSum());
        applicationReferenceMetric.setMqTransactionErrorDurationSum(instanceReferenceMetric.getMqTransactionErrorDurationSum());

        return applicationReferenceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceReferenceMetric, ApplicationReferenceMetric, ApplicationReferenceMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager,
            QueueCreatorService<InstanceReferenceMetric> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ApplicationReferenceMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationReferenceMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
