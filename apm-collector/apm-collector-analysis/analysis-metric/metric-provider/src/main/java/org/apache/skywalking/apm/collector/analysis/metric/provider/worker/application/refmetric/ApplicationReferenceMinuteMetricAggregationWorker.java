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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.refmetric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IApdexThresholdService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ApdexThresholdUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMinuteMetricAggregationWorker extends AggregationWorker<InstanceReferenceMetric, ApplicationReferenceMetric> {

    private final InstanceCacheService instanceCacheService;
    private final IApdexThresholdService apdexThresholdService;

    private ApplicationReferenceMinuteMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.instanceCacheService = moduleManager.find(CacheModule.NAME).getService(InstanceCacheService.class);
        this.apdexThresholdService = moduleManager.find(ConfigurationModule.NAME).getService(IApdexThresholdService.class);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.APPLICATION_REFERENCE_MINUTE_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected ApplicationReferenceMetric transform(InstanceReferenceMetric instanceReferenceMetric) {
        Integer frontApplicationId = instanceCacheService.getApplicationId(instanceReferenceMetric.getFrontInstanceId());
        Integer behindApplicationId = instanceCacheService.getApplicationId(instanceReferenceMetric.getBehindInstanceId());

        String metricId = frontApplicationId
            + Const.ID_SPLIT + behindApplicationId
            + Const.ID_SPLIT + instanceReferenceMetric.getSourceValue();

        String id = instanceReferenceMetric.getTimeBucket()
            + Const.ID_SPLIT + metricId;

        ApplicationReferenceMetric applicationReferenceMetric = new ApplicationReferenceMetric();
        applicationReferenceMetric.setId(id);
        applicationReferenceMetric.setMetricId(metricId);
        applicationReferenceMetric.setFrontApplicationId(frontApplicationId);
        applicationReferenceMetric.setBehindApplicationId(behindApplicationId);
        applicationReferenceMetric.setSourceValue(instanceReferenceMetric.getSourceValue());

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

        ApdexThresholdUtils.Apdex apdex = ApdexThresholdUtils.compute(apdexThresholdService.getApplicationApdexThreshold(behindApplicationId), instanceReferenceMetric.getTransactionDurationSum());
        if (ApdexThresholdUtils.Apdex.Satisfied.equals(apdex)) {
            applicationReferenceMetric.setSatisfiedCount(1L);
        } else if (ApdexThresholdUtils.Apdex.Tolerating.equals(apdex)) {
            applicationReferenceMetric.setToleratingCount(1L);
        } else {
            applicationReferenceMetric.setFrustratedCount(1L);
        }

        applicationReferenceMetric.setTimeBucket(instanceReferenceMetric.getTimeBucket());

        return applicationReferenceMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<InstanceReferenceMetric, ApplicationReferenceMetric, ApplicationReferenceMinuteMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationReferenceMinuteMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationReferenceMinuteMetricAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/aggregate/onWork/" + ApplicationReferenceMetricTable.TABLE)
    @Override protected void onWork(InstanceReferenceMetric message) throws WorkerException {
        super.onWork(message);
    }
}
